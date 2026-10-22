const DAY = 86400_000;
export const error = (status, code) => Response.json({ error: code }, { status, headers: { 'Cache-Control': 'no-store' } });

export function validate(input) {
  if (!input || !Array.isArray(input.points) || typeof input.loop !== 'boolean' || !['walk', 'bicycle', 'drive'].includes(input.profile)) throw Error('INVALID_REQUEST');
  let points = input.points;
  if (points.length < 2 || points.length > 20) throw Error('INVALID_POINTS');
  for (const p of points) if (!Array.isArray(p) || p.length !== 2 || !p.every(Number.isFinite) || Math.abs(p[0]) > 90 || Math.abs(p[1]) > 180) throw Error('INVALID_POINTS');
  if (input.loop && (points[0][0] !== points.at(-1)[0] || points[0][1] !== points.at(-1)[1])) points = [...points, points[0]];
  if (points.length > 20) throw Error('INVALID_POINTS');
  let distance = 0;
  const rad = x => x * Math.PI / 180;
  for (let i = 1; i < points.length; i++) {
    const [a,b] = [points[i-1], points[i]];
    const h = Math.sin(rad(b[0]-a[0])/2)**2 + Math.cos(rad(a[0]))*Math.cos(rad(b[0]))*Math.sin(rad(b[1]-a[1])/2)**2;
    distance += 12742017.6 * Math.asin(Math.sqrt(Math.min(1,h)));
  }
  if (distance > 100000 || distance < 1) throw Error('INVALID_DISTANCE');
  return { points, profile: input.profile, credits: points.length - 1 };
}

/** Reserve before every upstream attempt. Free-account limits are shared by all installations. */
export function withinBudget(events, now, credits) {
  const day = events.filter(e => e.time > now - DAY);
  return day.length < 100 && day.reduce((n,e) => n + e.credits,0) + credits <= 2000 &&
    day.filter(e => e.time > now - 60000).length < 10 && !day.some(e => e.time > now - 1000);
}

export class RoutingBudget {
  constructor(ctx) {
    this.ctx = ctx;
    ctx.storage.sql.exec('CREATE TABLE IF NOT EXISTS requests (id INTEGER PRIMARY KEY, time INTEGER NOT NULL, credits INTEGER NOT NULL)');
  }
  async fetch(request) {
    const body = await request.json();
    const { credits } = body;
    if (!Number.isInteger(credits) || credits < 1 || credits > 10000) return error(400,'INVALID_RESERVATION');
    if (body.action === 'settle') {
      if (!Number.isInteger(body.id) || body.id < 1) return error(400,'INVALID_RESERVATION');
      this.ctx.storage.sql.exec('UPDATE requests SET credits = MAX(credits, ?) WHERE id = ?', credits, body.id);
      return new Response(null,{status:204});
    }
    const now = Date.now();
    const accepted = this.ctx.storage.transactionSync(() => {
      this.ctx.storage.sql.exec('DELETE FROM requests WHERE time <= ?', now - DAY);
      const events = this.ctx.storage.sql.exec('SELECT time, credits FROM requests').toArray();
      if (!withinBudget(events,now,credits)) return null;
      return this.ctx.storage.sql.exec('INSERT INTO requests (time, credits) VALUES (?,?) RETURNING id',now,credits).one().id;
    });
    return accepted ? Response.json({id:accepted},{status:201}) : error(429,'QUOTA_EXHAUSTED');
  }
}

async function readBounded(stream, maximum) {
  if (!stream) throw Error('EMPTY_RESPONSE');
  const reader = stream.getReader(); let size=0; const chunks=[];
  try {
    while (true) {
      const {value, done} = await reader.read(); if (done) break;
      size += value.byteLength;
      if (size > maximum) throw Error('TOO_LARGE');
      chunks.push(value);
    }
  } catch (e) { await reader.cancel(); throw e; }
  const out = new Uint8Array(size); let offset=0;
  for (const chunk of chunks) { out.set(chunk,offset); offset+=chunk.byteLength; }
  return out;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== '/v1/route') return error(404,'NOT_FOUND');
    if (request.method !== 'POST') return error(405,'METHOD_NOT_ALLOWED');
    if (!env.GEOAPIFY_KEY || !env.BUDGET) return error(503,'UNAVAILABLE');
    let input;
    try {
      if (!(request.headers.get('content-type') || '').startsWith('application/json')) return error(415,'INVALID_CONTENT_TYPE');
      const bytes=await readBounded(request.body,8192);
      input=validate(JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes)));
    } catch { return error(400,'INVALID_REQUEST'); }
    try {
      const counter=env.BUDGET.get(env.BUDGET.idFromName('global-v1'));
      const budget=await counter.fetch('https://budget/reserve',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({credits:input.credits})});
      if (!budget.ok) return error(budget.status === 429 ? 429 : 503,budget.status === 429 ? 'QUOTA_EXHAUSTED' : 'UNAVAILABLE');
      const reservation=await budget.json();
      const upstream=new URL('https://api.geoapify.com/v1/routing');
      upstream.search=new URLSearchParams({apiKey:env.GEOAPIFY_KEY,waypoints:input.points.map(p=>p.join(',')).join('|'),
        mode:input.profile,type:'balanced',intermediate_waypoint_mode:'through_stop',units:'metric',lang:'es',format:'geojson'});
      // Build fresh headers without copying client cookies, IDs or Authorization.
      const response=await fetch(upstream,{headers:{Accept:'application/json'},signal:AbortSignal.timeout(20000),redirect:'error'});
      if (!response.ok) return error(response.status===429 || response.status===403 ? 429 : response.status===400 || response.status===404 ? 422 : 502,'ROUTING_FAILED');
      const bytes=await readBounded(response.body,2*1024*1024);
      const data=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
      const feature=data.features?.[0];
      const resultDistance=feature?.properties?.distance;
      if (Number.isFinite(resultDistance) && resultDistance>500000) {
        // Conservative rounding of the provider's surcharge, even for a rejected detour.
        const credits=input.credits+Math.ceil(resultDistance/500000);
        const settled=await counter.fetch('https://budget/settle',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action:'settle',id:reservation.id,credits})});
        if (!settled.ok) return error(503,'UNAVAILABLE');
      }
      if (data.features?.length!==1 || feature.geometry?.type!=='MultiLineString' || !Number.isFinite(feature.properties?.distance) || feature.properties.distance>100000) return error(422,'NO_ROUTE');
      // No caching or logging of request/response coordinates. Android validates the geometry too.
      return new Response(bytes,{headers:{'Content-Type':'application/geo+json','Cache-Control':'no-store'}});
    } catch { return error(503,'UNAVAILABLE'); }
  }
};
