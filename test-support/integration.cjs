'use strict'
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { spawn } = require('node:child_process')
const mineflayer = require('mineflayer')
const root = path.resolve(process.argv[2] || '')
const java = process.argv[3]
const baseline=process.argv.includes('--baseline'),commandsVersion=baseline?'1.0.0':'1.1.0'
const oldChat=false,chatVersion='0.1.3'
assert.equal(root, 'C:\\Users\\artyo\\Documents\\Codex\\nordcommands-paper-test-20261004')
assert(java)
const results = [], children = [], clients = new Set()
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
let paper, proxy, seq = 0
const source = path.resolve(__dirname, '..')
const data = path.join(root,'paper/plugins/NordFilter/data.yml')
const words = path.join(root,'paper/plugins/NordFilter/banwords.yml')
const filterConfig=path.join(root,'paper/plugins/NordFilter/config.yml')
const config=path.join(root,'paper/plugins/NordCommands/config.yml')
async function until(fn, label, timeout = 15000) {
  const start = Date.now()
  while (!await fn()) { if (Date.now() - start > timeout) throw Error('Timeout: ' + label); await sleep(50) }
}
function pass(name) { results.push(name); console.log('PASS: ' + name) }
function fixture() {
  const previous = 'C:\\Users\\artyo\\Documents\\Codex\\nordqueue-test-20261003'
  const secret = require('node:crypto').randomBytes(32).toString('hex')
  for (const sub of ['paper/plugins/NordChat', 'paper/plugins/NordFilter', 'paper/config', 'paper/plugins/NordCommands', 'proxy/plugins/nordqueue'])
    fs.mkdirSync(path.join(root, sub), { recursive: true })
  for(const version of ['0.1.2','0.1.3']){
    const previousJar=path.join(root,'paper/plugins/NordChat-'+version+'.jar')
    if(fs.existsSync(previousJar))fs.renameSync(previousJar,path.join(root,'retired-chat-'+version+'-'+Date.now()+'.jar'))
  }
  const chatJar=oldChat?'Z:\\Minecraft server\\plugins\\NordChat-0.1.2.jar':'Z:\\Minecraft Plagins\\NordChat\\releases\\0.1.3\\NordChat-0.1.3.jar'
  fs.copyFileSync(chatJar,path.join(root,'paper/plugins/NordChat-'+chatVersion+'.jar'))
  fs.copyFileSync(path.join(__dirname, 'build/CommandsTestProbe.jar'), path.join(root, 'paper/plugins/CommandsTestProbe.jar'))
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordFilter\\releases\\1.1.0\\NordFilter-1.1.0.jar',path.join(root,'paper/plugins/NordFilter-1.1.0.jar'))
  for(const version of ['1.0.0','1.1.0']){const jar=path.join(root,'paper/plugins/NordCommands-Paper-'+version+'.jar');if(fs.existsSync(jar))fs.renameSync(jar,path.join(root,'retired-commands-'+version+'-'+Date.now()+'.jar'))}
  fs.copyFileSync(baseline?'Z:\\Minecraft server\\plugins\\NordCommands-Paper-1.0.0.jar':path.join(source,'build/NordCommands-Paper-1.1.0.jar'),path.join(root,'paper/plugins/NordCommands-Paper-'+commandsVersion+'.jar'))
  fs.copyFileSync('Z:\\Minecraft Proxy\\plugins\\NordCommands-Velocity-1.0.0.jar',path.join(root,'proxy/plugins/NordCommands-Velocity-1.0.0.jar'))
  fs.writeFileSync(config,'denied-message: No such command.\nallowed-commands: [msg, safe, guarded, login, register]\n')
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordQueue\\releases\\1.1.2\\NordQueue-1.1.2.jar', path.join(root, 'proxy/plugins/NordQueue-1.1.2.jar'))
  fs.writeFileSync(path.join(root, 'paper/eula.txt'), 'eula=true\n')
  fs.writeFileSync(path.join(root, 'paper/server.properties'), [
    'server-ip=127.0.0.1','server-port=25686','online-mode=false','enforce-secure-profile=false',
    'max-players=20','view-distance=2','simulation-distance=2','enable-rcon=false','enable-query=false',
    'level-name=nordchat-synthetic-world','level-type=minecraft:flat','generate-structures=false',
    'spawn-protection=0','pause-when-empty-seconds=-1','gamemode=creative','force-gamemode=true','difficulty=peaceful',''].join('\n'))
  const original = fs.readFileSync(path.join(previous,'paper/config/paper-global.yml'), 'utf8')
  const velocitySection = /  velocity:\r?\n    enabled: (?:true|false)\r?\n    online-mode: (?:true|false)\r?\n    secret: [^\r\n]*/
  assert(velocitySection.test(original))
  fs.writeFileSync(path.join(root, 'paper/config/paper-global.yml'), original.replace(velocitySection,
    '  velocity:\n    enabled: true\n    online-mode: false\n    secret: "'+secret+'"'))
  fs.writeFileSync(path.join(root, 'paper/plugins/NordChat/config.yml'),
    'temporary-ignore-days: 7\nprivate-message-cooldown-millis: 500\nclickable-chat-names: true\n')
  fs.copyFileSync('Z:\\Minecraft Plagins\\NordFilter\\src\\main\\resources\\config.yml', path.join(root, 'paper/plugins/NordFilter/config.yml'))
  fs.writeFileSync(path.join(root, 'paper/plugins/NordFilter/banwords.yml'), 'words:\n  - syntheticforbidden\n')
  fs.writeFileSync(data,'{}\n')
  fs.writeFileSync(path.join(root,'paper/plugins/NordChat/players.yml'),'{}\n')
  fs.writeFileSync(path.join(root, 'proxy/local-test-forwarding.secret'), secret)
  let velocity = fs.readFileSync(path.join(previous,'proxy/velocity.toml'),'utf8')
    .replaceAll('25615','25685').replaceAll('25616','25686').replaceAll('25617','25687')
    .replace(/player-info-forwarding-mode = "[^"]+"/,'player-info-forwarding-mode = "modern"')
  fs.writeFileSync(path.join(root,'proxy/velocity.toml'),velocity)
  fs.writeFileSync(path.join(root, 'proxy/plugins/nordqueue/config.properties'),
    'main-capacity=20\nminimum-wait-seconds=1\ntransfer-interval-seconds=1\n')
  let limbo = fs.readFileSync(path.join(previous,'queue/settings.yml'),'utf8').replaceAll('25617','25687')
    .replace(/  type: (?:NONE|MODERN)/,'  type: MODERN').replace(/  secret: "[^"]+"/,'  secret: "'+secret+'"')
  assert(limbo.includes('type: MODERN'))
  fs.writeFileSync(path.join(root,'queue/settings.yml'),limbo)
}
function start(name, jar, heap) {
  const child = spawn(java, ['-Xms64M', '-Xmx' + heap, '-jar', jar, ...(name === 'paper' ? ['nogui'] : [])],
    { cwd: path.join(root, name), windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] })
  const handle = { child, name, output: '', exited: false, index: children.filter(c => c.name === name).length }
  children.push(handle)
  child.stdout.on('data', b => { handle.output += b.toString().replace(/\x1b\[[0-9;]*m/g, '') })
  child.stderr.on('data', b => { handle.output += b.toString().replace(/\x1b\[[0-9;]*m/g, '') })
  child.on('exit', () => { handle.exited = true })
  child.on('error', e => { handle.output += String(e); handle.exited = true })
  return handle
}
async function startPaper() {
  paper = start('paper', 'server.jar', '2G')
  await until(() => /Done \(/.test(paper.output) || paper.exited, 'Paper startup', 120000)
  assert(!paper.exited, paper.output.slice(-3000))
  assert(paper.output.includes('Enabling NordChat v'+chatVersion))
  assert.match(paper.output,/Enabling NordFilter v1\.1\.0/)
  assert(paper.output.includes('Enabling NordCommands v'+commandsVersion))
}
function connect(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25685, username: name, auth: 'offline',
    version: '26.2', hideErrors: true, checkTimeoutInterval: 30000 })
  const c = { bot, name, messages: [], originals: [], errors: [], ended: false, roots: [], commandPackets: 0, joinOffset: paper.output.length }
  clients.add(c)
  // Mineflayer retains the signed original in messagestr; a real client displays
  // the unsigned decorated component when Paper rewrites/renders a message.
  bot.on('message', m => c.messages.push(m.unsigned ? m.unsigned.toString() : m.toString()))
  bot._client.on('player_chat',packet=>c.originals.push(packet.plainMessage))
  bot._client.on('declare_commands',packet=>{c.commandPackets++; const rootNode=packet.nodes[packet.rootIndex??packet.root??0];c.roots=(rootNode.children||[]).map(i=>packet.nodes[i].extraNodeData?.name??packet.nodes[i].name).filter(Boolean)})
  bot.on('end', () => { c.ended = true })
  bot.on('error', e => c.errors.push(String(e)))
  bot.on('kicked', r => { c.kicked = JSON.stringify(r) })
  return c
}
async function joined(c) {
  await until(()=>paper.output.slice(c.joinOffset).includes(c.name+' joined the game')||c.ended,'join '+c.name,25000)
  assert(!c.ended,JSON.stringify({kicked:c.kicked,errors:c.errors}));await sleep(500)
}
async function disconnected(c) { if (!c.ended) c.bot.quit(); await until(() => c.ended, 'disconnect'); await sleep(300) }
async function expect(c, regex, from = 0) {
  await until(() => c.messages.slice(from).some(m => regex.test(m)) || c.ended, c.name + ' receives ' + regex)
  assert(c.messages.slice(from).some(m => regex.test(m)), JSON.stringify({ messages:c.messages,kicked:c.kicked,errors:c.errors }))
}
async function cmd(c, text, regex) { await sleep(1100); const mark=c.messages.length; c.bot.chat(text); await expect(c,regex,mark) }
async function probe(command) {
  const from=paper.output.length; paper.child.stdin.write('cptest '+command+'\n')
  await until(()=>paper.output.slice(from).includes('CPTEST_OK '+command)||paper.output.slice(from).includes('CPTEST_FAILED'),'probe '+command)
  assert(!paper.output.slice(from).includes('CPTEST_FAILED'),paper.output.slice(from))
}
async function state(){
  const id='s'+(++seq);await probe('state '+id)
  const m=paper.output.match(new RegExp('CPSTATE '+id+' safe=(\\d+) unsafe=(\\d+) guarded=(\\d+) workers=(\\d+) notices=(-?\\d+) refresh=(-?\\d+) raw=([^\\r\\n]*)'))
  assert(m,paper.output.slice(-1500));return{safe:+m[1],unsafe:+m[2],guarded:+m[3],workers:+m[4],notices:+m[5],refresh:+m[6],raw:Buffer.from(m[7],'base64').toString()}
}
async function admin(text,regex){
  const from=paper.output.length;paper.child.stdin.write('nordcommands '+text+'\n')
  await until(()=>regex.test(paper.output.slice(from)),'admin '+text)
}
async function reload(valid=true){
  const from=paper.output.length;await admin('reload',/reload queued/)
  await until(()=>paper.output.slice(from).includes(valid?'configuration reloaded.':'reload rejected;'),'reload outcome',15000)
}
async function tab(name,buffer,cancelled){
  const id='t'+(++seq);await probe('tab '+name+' '+id+' '+Buffer.from(buffer).toString('base64'))
  assert(paper.output.includes('CPTAB '+id+' cancelled='+cancelled+' count='+(cancelled?0:1)))
}
async function stopPaper() {
  if(paper&&!paper.exited){paper.child.stdin.write('stop\n');await until(()=>paper.exited,'Paper shutdown',45000)}
}
async function cleanup() {
  for(const c of clients)if(!c.ended)c.bot.quit()
  if(proxy&&!proxy.exited){proxy.child.stdin.write('shutdown\n');await until(()=>proxy.exited,'proxy shutdown',20000)}
  await stopPaper()
  for(const h of children.filter(c=>c.name==='queue'&&!c.exited)){
    h.child.kill();await until(()=>h.exited,'owned isolated limbo shutdown',10000)
  }
  for(const h of children)fs.writeFileSync(path.join(root,h.name+'-'+h.index+'-runtime.log'),h.output)
}


async function run(){
  fixture();await startPaper()
  const queue=start('queue','NanoLimbo.jar','256M');await until(()=>/NanoLimbo started|Listening|Server started/.test(queue.output)||queue.exited,'limbo startup',30000);assert(!queue.exited,queue.output)
  proxy=start('proxy','velocity.jar','512M');await until(()=>/Done \(/.test(proxy.output)||proxy.exited,'proxy startup',30000);assert(!proxy.exited,proxy.output)
  let a=connect('CPAlpha');await joined(a);let b=connect('CPBeta');await joined(b)
  await cmd(a,'/safe ordinary',/SAFE_EXECUTED ordinary/)
  await cmd(a,'/msg CPBeta NORMAL_PM',/NORMAL_PM/);await expect(b,/NORMAL_PM/)
  pass('Allowed commands and NordChat PM work through current proxy command filter and MODERN transfer')
  await sleep(350);await cmd(a,'/unsafe direct',/No such command/);assert.equal((await state()).unsafe,0)
  if(baseline){
    await cmd(a,'/safe rewrite',/UNSAFE_EXECUTED/);assert.equal((await state()).unsafe,1)
    await cmd(a,'/safe namespace',/UNSAFE_EXECUTED/);assert.equal((await state()).unsafe,2)
    pass('OLD CODE: reproduced routing bypass after NORMAL event rewrite, including namespace')
    const mark=a.messages.length;await probe('direct CPAlpha reload');await expect(a,/configuration reloaded/,mark)
    pass('OLD CODE: direct admin executor accepts unauthorized reload')
    return
  }
  pass('Direct unlisted synthetic command never reaches executor')
  for(const text of ['/safe rewrite','/safe namespace','/commandstestprobe:safe x','/minecraft:msg CPBeta NAMESPACE_DENIED','/nordchat:msg CPBeta NAMESPACE_DENIED']){
    await sleep(350);await cmd(a,text,/No such command/)
  }
  assert.equal((await state()).unsafe,0)
  await cmd(a,'/safe alias',/ALLOWED_REWRITE/);await expect(b,/ALLOWED_REWRITE/)
  pass('Final gate blocks rewritten disallowed/namespaced commands but permits allowed-to-allowed routing')
  const raw='/safe two  spaces ; /op FakeSynthetic'
  await cmd(a,raw,/SAFE_EXECUTED/);assert.equal((await state()).raw,raw)
  pass('Argument payload is preserved; command-like text remains synthetic command arguments')
  await sleep(350);await cmd(a,'/guarded',/permission|unknown|command/i);assert.equal((await state()).guarded,0)
  await probe('permission CPAlpha commandstest.guarded true')
  await cmd(a,'/guarded',/GUARDED_EXECUTED/);assert.equal((await state()).guarded,1)
  await probe('permission CPAlpha commandstest.guarded false')
  await cmd(a,'/guarded',/permission|unknown|command/i);assert.equal((await state()).guarded,1)
  pass('Whitelist grants no underlying command permission; current permission revocation works')
  await sleep(350);await cmd(a,'/nordcommands reload',/No such command|permission|unknown/i)
  const denial=a.messages.length;await probe('direct CPAlpha reload');await expect(a,/do not have permission/,denial)
  pass('Management metadata and direct executor both reject unauthorized player')
  await probe('permission CPAlpha nordcommands.admin true')
  await cmd(a,'/nordcommands health',/NordCommands: ready/)
  await cmd(a,'/nordcommands:nordcommands health',/NordCommands: ready/)
  const from=paper.output.length;await cmd(a,'/nordcommands reload',/reload queued/)
  await until(()=>paper.output.slice(from).includes('configuration reloaded.'),'authorized management reload')
  pass('Specific admin permission permits owned management without granting broad bypass')
  await until(()=>b.commandPackets>0,'backend command tree')
  assert(b.roots.includes('safe')&&b.roots.includes('msg'),JSON.stringify(b.roots))
  assert(!b.roots.includes('unsafe')&&!b.roots.includes('minecraft:msg')&&!b.roots.includes('nordcommands'),JSON.stringify(b.roots))
  await tab(b.name,'/unsafe ',true);await tab(b.name,'/safe ',false)
  pass('Command tree and argument-completion event hide/deny unlisted roots')
  await probe('permission CPBeta nordcommands.bypass true')
  await cmd(b,'/unsafe intentional',/UNSAFE_EXECUTED/)
  await probe('permission CPBeta nordcommands.bypass false')
  await sleep(350);await cmd(b,'/unsafe revoked',/No such command/)
  assert.equal((await state()).unsafe,1)
  pass('Intentional bypass is permission-checked and immediately revocable')
  const before=fs.readFileSync(config);await probe('slow 2000')
  let mark=paper.output.length;await admin('reload',/reload queued/)
  const begin=Date.now();await admin('health',/reload pending=true/);assert(Date.now()-begin<1500)
  await admin('reload',/already pending/)
  await until(()=>paper.output.slice(mark).includes('configuration reloaded.'),'slow reload')
  await probe('slow 0');assert.equal((await state()).workers,1)
  assert(fs.readFileSync(config).equals(before))
  pass('Slow config read leaves main thread responsive; repeated reload stays bounded to one reader')
  fs.writeFileSync(config,'allowed-commands: ["/safe forbidden"]\n')
  await reload(false);await cmd(b,'/safe old-policy-still-active',/SAFE_EXECUTED/)
  await sleep(350);await cmd(b,'/unsafe remains-denied',/No such command/)
  fs.writeFileSync(config,before);await reload()
  pass('Invalid reload retains prior complete policy without truncating a bad entry into a broader label')
  const packetMark=b.commandPackets
  fs.writeFileSync(config,'denied-message: Custom denial.\nallowed-commands: [msg, guarded, login, register]\n')
  await reload();await until(()=>b.commandPackets>packetMark,'refreshed command tree')
  assert(!b.roots.includes('safe'),JSON.stringify(b.roots))
  await sleep(350);await cmd(b,'/safe removed',/Custom denial/)
  fs.writeFileSync(config,before);await reload()
  await until(()=>b.roots.includes('safe'),'restored command tree')
  pass('Valid reload changes execution policy and refreshes already-connected clients')
  for(let i=0;i<3;i++)await reload();assert.equal((await state()).workers,1)
  const asyncId='a'+(++seq);await probe('async CPBeta '+asyncId)
  await until(()=>paper.output.includes('CPASYNC '+asyncId+' cancelled=true'),'offthread callback rejected')
  pass('Repeated reload retains one reader; synthetic off-thread callback fails closed')
  await sleep(350);await cmd(b,'/msg CPAlpha syntheticforbidden',/muted/)
  const mutedFrom=a.messages.length;b.bot.chat('FILTER_STILL_BLOCKS');await sleep(500)
  assert(!a.messages.slice(mutedFrom).some(m=>m.includes('FILTER_STILL_BLOCKS')))
  paper.child.stdin.write('nordfilter reset CPBeta\n');await sleep(750)
  b.bot.chat('AFTER_FILTER_RESET');await expect(a,/AFTER_FILTER_RESET/)
  pass('Prepared NordChat and NordFilter continue to enforce PM/public moderation alongside command policy')
  await disconnected(a);await disconnected(b);await until(async()=>(await state()).notices===0,'quit cleans denial bookkeeping')
  await stopPaper();fs.writeFileSync(config,'allowed-commands: ["/safe"]\n')
  await startPaper();a=connect('CPAlpha');await joined(a);b=connect('CPBeta');await joined(b)
  await cmd(a,'/safe blocked-at-start',/policy is unavailable/)
  await probe('permission CPAlpha nordcommands.bypass true')
  await sleep(350);await cmd(a,'/unsafe bypass-while-broken',/policy is unavailable/)
  assert.equal((await state()).unsafe,0)
  await probe('permission CPAlpha nordcommands.admin true')
  await cmd(a,'/nordcommands health',/NordCommands: blocked/)
  fs.writeFileSync(config,before);await reload();await probe('permission CPAlpha nordcommands.bypass false')
  await cmd(a,'/safe recovered',/SAFE_EXECUTED/)
  assert.equal((await state()).workers,1)
  pass('Invalid initialization blocks even bypass execution, allows explicit management, and recovers without plugin restart')
}
run().then(()=>console.log((baseline?'BASELINE_COMMAND_CHECKS_PASSED':'ALL_COMMAND_INTEGRATION_TESTS_PASSED')+' count='+results.length))
 .catch(e=>{console.error(e.stack);if(paper)console.error(paper.output.slice(-5000));for(const c of clients)console.error(c.name,JSON.stringify({messages:c.messages.slice(-15),roots:c.roots,kicked:c.kicked,errors:c.errors}));process.exitCode=1})
 .finally(async()=>{try{await cleanup()}catch(e){console.error(e.stack);process.exitCode=1}
 fs.writeFileSync(path.join(root,'results.json'),JSON.stringify({passed:process.exitCode!==1,commandsVersion,chatVersion,results},null,2))})

