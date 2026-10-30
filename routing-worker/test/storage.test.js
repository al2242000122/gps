import test from 'node:test';
import assert from 'node:assert/strict';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

const options=persist=>convertV4MiniflareOptions({modules:true,scriptPath:new URL('../src/index.js',import.meta.url).pathname,
  compatibilityDate:'2025-10-01',durableObjects:{BUDGET:{className:'RoutingBudget',useSQLite:true}},resourcePersistencePath:persist});
async function stub(mf){const ns=await mf.getDurableObjectNamespace('BUDGET');return ns.get(ns.idFromName('global-v1'));}
const reserve=(s,body)=>s.fetch('https://budget/reserve',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
test('SQLite serializes simultaneous reservations, settles costs and survives restart',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'gps-budget-'));let mf;
  try {
    mf=new Miniflare(options(dir));let s=await stub(mf);
    const responses=await Promise.all(Array.from({length:12},()=>reserve(s,{credits:1})));
    assert.equal(responses.filter(r=>r.status===201).length,1);
    const token=await responses.find(r=>r.status===201).json();
    assert.equal((await reserve(s,{action:'settle',id:token.id,credits:2000})).status,204);
    await mf.dispose();mf=new Miniflare(options(dir));s=await stub(mf);
    assert.equal((await reserve(s,{credits:1})).status,429);
  } finally {if(mf)await mf.dispose();await rm(dir,{recursive:true,force:true});}
});
