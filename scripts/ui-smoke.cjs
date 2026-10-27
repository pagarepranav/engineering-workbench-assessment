/* Optional DOM behavior smoke check. No browser install or frontend build required.
 * npm install --prefix tmp/ui-check jsdom@26 --no-audit --no-fund
 * node scripts/ui-smoke.cjs
 * Creates labeled review data in the running local app; never approves code execution.
 */
const assert=require('node:assert/strict');
const path=require('node:path');
const {JSDOM,VirtualConsole}=require(path.resolve('tmp/ui-check/node_modules/jsdom'));
const origin=process.env.WORKBENCH_URL||'http://127.0.0.1:8080';
async function main(){
    const errors=[];const console=new VirtualConsole();console.on('jsdomError',e=>errors.push(e.message));
    const dom=await JSDOM.fromURL(origin,{resources:'usable',runScripts:'dangerously',pretendToBeVisual:true,virtualConsole:console,beforeParse(window){window.fetch=(url,options)=>fetch(new URL(url,origin),options);}});
    const w=dom.window,d=w.document;
    const wait=async(predicate,label)=>{const end=Date.now()+15000;while(Date.now()<end){if(predicate())return;await new Promise(r=>setTimeout(r,50));}throw Error('Timed out: '+label+'; toast='+d.querySelector('#toast').textContent);};
    const click=text=>{const b=Array.from(d.querySelectorAll('button')).find(b=>b.textContent===text);assert.ok(b,'Button '+text);b.click();};
    const submit=id=>d.querySelector(id).dispatchEvent(new w.Event('submit',{bubbles:true,cancelable:true}));
    try {
        await wait(()=>d.querySelectorAll('.metric').length===4,'overview metrics');
        d.querySelector('[data-view="links"]').click();
        await wait(()=>!d.querySelector('#view-links').hidden,'link screen');
        const alias='ui-'+Date.now();d.querySelector('[name="destination"]').value='https://example.com/ui-smoke';d.querySelector('[name="alias"]').value=alias;
        submit('#link-form');await wait(()=>!d.querySelector('#created-link').hidden,'link creation');
        assert.ok(d.querySelector('#created-link').textContent.includes(alias));
        await wait(()=>Array.from(d.querySelectorAll('#link-rows tr')).some(r=>r.textContent.includes(alias)),'link list refresh');
        const row=Array.from(d.querySelectorAll('#link-rows tr')).find(r=>r.textContent.includes(alias));row.querySelector('button').click();
        await wait(()=>d.querySelector('.analytics-total'),'analytics rendering');
        assert.equal(d.querySelector('.analytics-total').textContent,'0');
        row.querySelectorAll('button')[1].click();
        await wait(()=>Array.from(d.querySelectorAll('#link-rows tr')).find(r=>r.textContent.includes(alias))?.textContent.includes('DISABLED'),'disable link');
        const disabledRow=Array.from(d.querySelectorAll('#link-rows tr')).find(r=>r.textContent.includes(alias));
        disabledRow.querySelectorAll('button')[1].click();
        await wait(()=>Array.from(d.querySelectorAll('#link-rows tr')).find(r=>r.textContent.includes(alias))?.textContent.includes('ACTIVE'),'enable link');
        d.querySelector('[data-view="workflows"]').click();
        await wait(()=>!d.querySelector('#view-workflows').hidden,'workflow screen');
        d.querySelector('#requirement').value='UI smoke: build a basic URL service with alias validation and click counts.';
        submit('#run-form');await wait(()=>d.querySelector('#run-detail .approval'),'authored demonstration execution gate');
        assert.ok(d.querySelector('#run-detail').textContent.includes('AUTHORED_DEMO_FIXTURE'));
        assert.equal(d.querySelectorAll('.task').length,8);
        const approve=Array.from(d.querySelectorAll('button')).find(b=>b.textContent==='Approve execution');assert.ok(approve.disabled,'Approval requires checkbox');
        d.querySelector('.check-label input').click();assert.equal(approve.disabled,false);
        // Do not click approval: human review remains an explicit product action.
        click('Safe stop');await wait(()=>d.querySelector('#run-detail .run-header').textContent.includes('STOPPED'),'safe stop');
        click('Resume');await wait(()=>d.querySelector('#run-detail .approval'),'resumed approval gate');
        click('+ New workflow');d.querySelector('[name="mode"]').value='MANUAL';d.querySelector('#requirement').value='UI smoke: manual ChatGPT handoff';submit('#run-form');
        await wait(()=>d.querySelector('.handoff pre')?.textContent.includes('acceptanceCriteria'),'manual prompt export');
        d.querySelector('.handoff textarea').value=JSON.stringify({summary:'Local URL service',acceptanceCriteria:['Redirect valid URLs'],questions:[],assumptions:['Single user']});
        click('Import reviewed response');await wait(()=>d.querySelector('.handoff h3')?.textContent.includes('PLAN'),'manual analysis import');
        assert.equal(errors.length,0,errors.join('\n'));
        process.stdout.write(JSON.stringify({status:'PASS',checks:['overview metrics','link creation and list','analytics rendering','disable link','enable link','eight workflow tasks','approval checkbox','safe stop and resume','manual prompt export','manual analysis import'],visualReview:false,codeExecutionApproved:false},null,2)+'\n');
    } finally {await new Promise(r=>setTimeout(r,400));w.close();}
}
main().catch(e=>{process.stderr.write(e.stack+'\n');process.exitCode=1;});
