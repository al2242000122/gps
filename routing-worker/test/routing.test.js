import test from 'node:test';
import assert from 'node:assert/strict';
import worker, {validate, withinBudget, RoutingBudget} from '../src/index.js';
const points=[[19.43,-99.13],[19.431,-99.131]];
test('profiles, coordinate bounds and circular return',()=>{
  for(const profile of ['walk','bicycle','drive']) assert.equal(validate({points,profile,loop:true}).points.length,3);
  assert.throws(()=>validate({points:[[91,0],[0,0]],profile:'walk',loop:false}));
  assert.throws(()=>validate({points:Array(20).fill(points[0]).map((p,i)=>[p[0]+i*.001,p[1]]),profile:'walk',loop:true}));
  assert.throws(()=>validate({points:[[0,0],[50,50]],profile:'walk',loop:false}));
});
test('rolling budgets include failures and do not reset at midnight',()=>{
  const now=100000000;
  assert.equal(withinBudget([],now,19),true);
  assert.equal(withinBudget([{time:now-999,credits:1}],now,1),false);
  assert.equal(withinBudget(Array.from({length:10},(_,i)=>({time:now-2000-i*2000,credits:1})),now,1),false);
  assert.equal(withinBudget(Array.from({length:100},(_,i)=>({time:now-100000-i,credits:1})),now,1),false);
  assert.equal(withinBudget([{time:now-100000,credits:1999}],now,2),false);
  assert.equal(withinBudget([{time:now-86400001,credits:2000}],now,1),true);
});
test('proxy fails closed when quota storage fails; no upstream request',async()=>{
  const request=new Request('https://example.com/v1/route',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({points,profile:'walk',loop:false})});
  const response=await worker.fetch(request,{GEOAPIFY_KEY:'test',BUDGET:{idFromName:()=>1,get:()=>({fetch:()=>{throw Error('offline')}})}});
  assert.equal(response.status,503);
});
test('unconfigured proxy and arbitrary paths are rejected',async()=>{
  assert.equal((await worker.fetch(new Request('https://example.com/v1/route',{method:'POST'}),{})).status,503);
  assert.equal((await worker.fetch(new Request('https://example.com/anything'),{})).status,404);
});

function request(body={points,profile:'walk',loop:false}) {
  return new Request('https://example.com/v1/route',{method:'POST',headers:{'content-type':'application/json','x-forwarded-for':'192.0.2.1',authorization:'Bearer client'},body:JSON.stringify(body)});
}
function environment(calls=[]) {
  return {GEOAPIFY_KEY:'synthetic-key',BUDGET:{idFromName:()=>1,get:()=>({fetch:async(_url,init)=>{
    const body=JSON.parse(init.body);calls.push(body);return body.action==='settle'?new Response(null,{status:204}):Response.json({id:1},{status:201});
  }})}};
}
function route(distance=200) {
  return {features:[{geometry:{type:'MultiLineString',coordinates:[[[-99.13,19.43],[-99.131,19.431]]]},properties:{distance,time:120}}]};
}
test('proxy preserves lat-lon input and excludes phone headers from upstream',async()=>{
  const original=globalThis.fetch;
  try {
    globalThis.fetch=async(url,init)=>{
      assert.equal(url.hostname,'api.geoapify.com');assert.equal(url.searchParams.get('waypoints'),'19.43,-99.13|19.431,-99.131');
      assert.equal(url.searchParams.get('mode'),'walk');assert.deepEqual(init.headers,{Accept:'application/json'});
      return Response.json(route());
    };
    const response=await worker.fetch(request(),environment());
    assert.equal(response.status,200);assert.equal(response.headers.get('cache-control'),'no-store');
    assert.deepEqual(await response.json(),route());
  } finally {globalThis.fetch=original;}
});
test('quota errors timeout and invalid responses stay bounded without retry',async()=>{
  const original=globalThis.fetch;
  try {
    for(const status of [403,429,400,500]) {
      let count=0;globalThis.fetch=async()=>{count++;return new Response('',{status});};
      assert.equal((await worker.fetch(request(),environment())).status,status===403||status===429?429:status===400?422:502);
      assert.equal(count,1);
    }
    globalThis.fetch=async()=>{throw Error('timeout');};
    assert.equal((await worker.fetch(request(),environment())).status,503);
    globalThis.fetch=async()=>new Response('x'.repeat(2*1024*1024+1));
    assert.equal((await worker.fetch(request(),environment())).status,503);
    const calls=[];
    assert.equal((await worker.fetch(request({points,profile:'walk',loop:false,extra:'x'.repeat(9000)}),environment(calls))).status,400);
    assert.equal(calls.length,0);
  } finally {globalThis.fetch=original;}
});
test('a billed long detour settles conservative credits even when rejected',async()=>{
  const original=globalThis.fetch;const calls=[];
  try {
    globalThis.fetch=async()=>Response.json(route(500001));
    assert.equal((await worker.fetch(request(),environment(calls))).status,422);
    assert.deepEqual(calls,[{credits:1},{action:'settle',id:1,credits:3}]);
  } finally {globalThis.fetch=original;}
});
