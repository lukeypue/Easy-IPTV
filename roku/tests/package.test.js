const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
test('Roku package has branded entry, one video and all matching menu sections',()=>{
 const xml=fs.readFileSync(path.join(root,'components/RyzodScene.xml'),'utf8');
 assert.equal((xml.match(/<Video\b/g)||[]).length,1);
 for(const id of ['shell','rail','content','video','guide','keyboard','actions','mini','schedule']) assert.ok(xml.includes(`id="${id}"`),id);
 const code=fs.readFileSync(path.join(root,'components/RyzodScene.brs'),'utf8');
 for(const section of ['Live TV','Movies','Series','Search','Downloads','Recordings','Playlists','Settings']) assert.ok(code.includes('"'+section+'"'),section);
 assert.ok(fs.existsSync(path.join(root,'source/main.brs')));
});
test('native keyboard and action panel consume Back and remote events',()=>{
 for(const component of ['RyzodKeyboard','RyzodActions','RyzodGuide','RyzodSchedule']){
 const xml=fs.readFileSync(path.join(root,'components',component+'.xml'),'utf8');
 const brs=fs.readFileSync(path.join(root,'components',component+'.brs'),'utf8');
 assert.match(xml,/<component\b/);assert.match(brs,/function onKeyEvent/);assert.match(brs,/"back"/);
 }
});
test('network worker has asynchronous timeout and carries playlist generation',()=>{
 const code=fs.readFileSync(path.join(root,'components/RequestTask.brs'),'utf8');
 assert.match(code,/asyncGetToString/);assert.match(code,/asyncCancel/);assert.match(code,/generation/);assert.match(code,/wait\(/);
 assert.doesNotMatch(code,/\bgetToString\(/i);
});
