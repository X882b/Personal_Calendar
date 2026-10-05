// Two phones syncing through a fake GitHub Contents API.
// Run: node tests/two-phones.js   (SHOTS=/some/dir to also save screenshots)
// Needs Playwright with Chromium; not needed to run the app itself.
let pw;
try{ pw = require('playwright'); }catch(e){ pw = require('/opt/node22/lib/node_modules/playwright'); }
const { chromium, devices } = pw;
const http = require('http'), fs = require('fs'), path = require('path'), os = require('os');
const ROOT = path.join(__dirname, '..');
const SHOTS = process.env.SHOTS || fs.mkdtempSync(path.join(os.tmpdir(), 'schedule-shots-'));
fs.mkdirSync(SHOTS, {recursive:true});
const types = {'.html':'text/html','.js':'text/javascript','.json':'application/json','.png':'image/png'};
const srv = http.createServer((q,r)=>{ const f = path.join(ROOT, decodeURIComponent(q.url.split('?')[0]) === '/' ? 'index.html' : decodeURIComponent(q.url.split('?')[0])); fs.readFile(f,(e,b)=>{ if(e){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':types[path.extname(f)]||'application/octet-stream'}); r.end(b); }); }).listen(8123);

// fake GitHub contents API with sha compare-and-swap
const repo = {file:null, sha:null, n:0, commits:[], down:new Set(), raceOnce:false};
let fail = 0;
const assert = (c, m) => { if(!c){ console.log('FAIL:', m); fail++; } else console.log('ok:', m); };
async function fakeGitHub(ctx, tag){
  await ctx.route('https://api.github.com/**', async route => {
    const req = route.request();
    if(repo.down.has(tag)) return route.abort('internetdisconnected');
    const cors = {'Access-Control-Allow-Origin':'*','Content-Type':'application/json'};
    if(!req.url().endsWith('/repos/test/data/contents/calendar.json')) return route.fulfill({status:404, headers:cors, body:'{}'});
    if(req.headers()['authorization'] !== 'Bearer tok123') return route.fulfill({status:401, headers:cors, body:'{}'});
    if(req.method() === 'GET'){
      if(!repo.file) return route.fulfill({status:404, headers:cors, body:'{"message":"Not Found"}'});
      return route.fulfill({status:200, headers:cors, body:JSON.stringify({sha:repo.sha, encoding:'base64', content:Buffer.from(repo.file).toString('base64').replace(/(.{60})/g,'$1\n')})});
    }
    if(req.method() === 'PUT'){
      const b = JSON.parse(req.postData());
      if(repo.raceOnce){ // the other phone writes between our GET and PUT
        repo.raceOnce = false; repo.sha = 'race' + (++repo.n);
      }
      if(repo.file && !b.sha) return route.fulfill({status:422, headers:cors, body:'{"message":"sha wasn\'t supplied"}'});
      if(repo.file && b.sha !== repo.sha) return route.fulfill({status:409, headers:cors, body:'{"message":"conflict"}'});
      repo.file = Buffer.from(b.content, 'base64').toString('utf8'); repo.sha = 'sha' + (++repo.n); repo.commits.push(b.message);
      return route.fulfill({status:repo.n === 1 ? 201 : 200, headers:cors, body:JSON.stringify({content:{sha:repo.sha}})});
    }
    route.fulfill({status:405, headers:cors, body:'{}'});
  });
}
const remoteEvents = () => JSON.parse(repo.file).events;
const iso = d => d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
const day = n => { const d = new Date(); d.setDate(d.getDate()+n); return iso(d); };

async function addEvent(p, {title, who, date, last, from, to, repeat, note}){
  await p.click('#add');
  await p.fill('#f-title', title);
  await p.click(`[data-seg="who"] [data-v="${who}"]`);
  if(date) await p.fill('#f-date', date);
  if(last) await p.fill('#f-last', last);
  if(from) await p.fill('#f-from', from);
  if(to) await p.fill('#f-to', to);
  if(repeat) await p.click(`[data-seg="repeat"] [data-v="${repeat}"]`);
  if(note) await p.fill('#f-note', note);
  await p.click('[data-act="saveEv"]');
}

(async()=>{
  const browser = await chromium.launch();
  const phone = {...devices['Pixel 7']};
  const A = await browser.newContext(phone), B = await browser.newContext(phone);
  await fakeGitHub(A,'A'); await fakeGitHub(B,'B');
  const pa = await A.newPage(), pb = await B.newPage();
  const errs = [];
  for(const [t,p] of [['A',pa],['B',pb]]) { p.on('pageerror', e=>errs.push(t+': '+e.message)); p.on('console', m=>{ if(m.type()==='error' && !/Failed to load resource/.test(m.text())) errs.push(t+' console: '+m.text()); }); }

  // --- phone A: first run ---
  await pa.goto('http://localhost:8123/');
  await pa.waitForSelector('#w-a');
  await pa.screenshot({path:SHOTS+'/01-welcome.png'});
  await pa.fill('#w-a','Ana'); await pa.fill('#w-b','Marko');
  await pa.click('[data-act="welcomeDone"]');
  assert(await pa.isVisible('#w-a'), 'welcome refuses to close until a phone owner is picked');
  await pa.click('[data-seg="me"] [data-v="a"]');
  await pa.click('[data-act="welcomeDone"]');
  assert(!(await pa.isVisible('#sheetbox h2')), 'welcome closes');

  await addEvent(pa, {title:'Dentist', who:'a', date:day(0), from:'00:01', to:'00:02', note:'Dr. Petrović, room 4'});
  await addEvent(pa, {title:'Dinner with friends', who:'ab', date:day(1), from:'19:30'});
  await addEvent(pa, {title:'Swimming', who:'b', date:day(2), from:'18:00', to:'19:00', repeat:'w'});
  await addEvent(pa, {title:'Trip to Novi Sad', who:'ab', date:day(9), last:day(12)});
  await addEvent(pa, {title:'Mum’s birthday', who:'ab', date:day(5), repeat:'y'});
  await addEvent(pa, {title:'Parent meeting <school>', who:'b', date:day(3), from:'17:00'});
  await addEvent(pa, {title:'Gym', who:'a', date:day(1), from:'07:00', to:'08:00'});
  const upText = await pa.textContent('#view');
  assert(upText.includes('Today') && upText.includes('Tomorrow'), 'upcoming shows Today and Tomorrow');
  assert(upText.includes('Parent meeting <school>'), 'titles are escaped, not injected');
  assert((await pa.$$('.ev')).length >= 7, 'events listed: ' + (await pa.$$('.ev')).length);
  assert(await pa.$eval('.dayb.today .ev', e=>e.classList.contains('past')), 'finished event today is greyed');
  // swimming repeats weekly: should be on day 2 and day 9
  const swimDays = await pa.$$eval('.dayb', bs=>bs.filter(b=>b.textContent.includes('Swimming')).length);
  assert(swimDays >= 2, 'weekly repeat appears on multiple days: ' + swimDays);
  const tripDays = await pa.$$eval('.dayb', bs=>bs.filter(b=>b.textContent.includes('Trip to Novi Sad')).length);
  assert(tripDays === 4, 'multi-day trip covers 4 days: ' + tripDays);
  assert(upText.includes('day 2 of 4'), 'trip shows "day 2 of 4"');
  await pa.screenshot({path:SHOTS+'/02-upcoming.png', fullPage:false});
  await pa.screenshot({path:SHOTS+'/02b-upcoming-full.png', fullPage:true});

  // filter
  await pa.click('[data-act="filter"][data-v="b"]');
  const fText = await pa.textContent('#view');
  assert(!fText.includes('Dentist') && fText.includes('Swimming') && fText.includes('Dinner'), 'filter shows only Marko + shared');
  await pa.click('[data-act="filter"][data-v="all"]');

  // month
  await pa.click('[data-tab="month"]');
  await pa.screenshot({path:SHOTS+'/03-month.png'});
  const dots = await pa.$$('.cell .dot');
  assert(dots.length > 5, 'month has dots: ' + dots.length);
  await pa.click(`[data-act="pickDay"][data-d="${day(1)}"]`);
  const sel = await pa.textContent('.selday');
  assert(sel.includes('Dinner with friends') && sel.includes('Gym'), 'picking a day lists its events');
  assert(sel.indexOf('Gym') < sel.indexOf('Dinner'), 'sorted by time');
  await pa.click('[data-act="month"][data-v="1"]');
  await pa.click('[data-act="month"][data-v="-1"]');

  // edit sheet + skip one occurrence of a repeating event
  await pa.click('[data-tab="up"]');
  await pa.locator('.ev', {hasText:'Swimming'}).first().click();
  await pa.screenshot({path:SHOTS+'/04-edit.png'});
  await pa.click('[data-act="skipDay"]');
  const swim2 = await pa.$$eval('.dayb', bs=>bs.filter(b=>b.textContent.includes('Swimming')).length);
  assert(swim2 === swimDays - 1, 'removing one occurrence keeps the rest: ' + swim2);
  await pa.click('#add');
  await pa.screenshot({path:SHOTS+'/05-new.png'});
  await pa.click('[data-act="saveEv"]');
  assert(await pa.$eval('#f-title', e=>e.classList.contains('bad')), 'empty title is refused');
  await pa.goBack(); // Android back closes the sheet
  assert(!(await pa.isVisible('#f-title')), 'back button closes the sheet');

  // --- connect sync on A ---
  assert((await pa.textContent('#sync')).includes('Not shared'), 'header says not shared before setup');
  await pa.click('#gear');
  await pa.fill('#f-repo', 'https://github.com/test/data');
  await pa.fill('#f-token', 'wrong');
  await pa.click('[data-act="saveSync"]');
  await pa.waitForFunction(()=>document.querySelector('#sync').textContent.includes('problem'));
  assert((await pa.textContent('#s-status')).includes('refused the token'), 'bad token explained');
  await pa.screenshot({path:SHOTS+'/06-settings-error.png'});
  await pa.click('[data-act="unlink"]');
  await pa.fill('#f-repo', 'test/data');
  await pa.fill('#f-token', 'tok123');
  await pa.click('[data-act="saveSync"]');
  await pa.waitForFunction(()=>document.querySelector('#sync').textContent.includes('Synced'));
  assert(remoteEvents().length === 7, 'A pushed 7 events to repo');
  assert(repo.file.split('\n').length > 8, 'file is one event per line');
  await pa.screenshot({path:SHOTS+'/07-settings-synced.png'});
  const link = await pa.evaluate(()=>setupLink());
  await pa.click('[data-act="settings"]').catch(()=>{});
  await pa.goBack();

  // --- phone B joins with the link ---
  await pb.goto(link);
  await pb.waitForSelector('#w-a');
  assert(await pb.inputValue('#w-a') === 'Ana' && await pb.inputValue('#w-b') === 'Marko', 'B gets the names from the repo');
  assert(!(await pb.url()).includes('join'), 'token removed from the address bar');
  await pb.click('[data-seg="me"] [data-v="b"]');
  await pb.click('[data-act="welcomeDone"]');
  assert((await pb.textContent('#view')).includes('Dinner with friends'), 'B sees A\'s events');
  await addEvent(pb, {title:'Football', who:'b', date:day(1), from:'20:00'});
  await pb.waitForFunction(()=>document.querySelector('#sync').textContent.includes('Synced') && !JSON.parse(localStorage.schedule_local).dirty);
  assert(remoteEvents().some(e=>e.title==='Football' && e.by==='b'), 'B pushed Football');
  await pa.evaluate(()=>sync());
  await pa.waitForFunction(()=>document.querySelector('#view').textContent.includes('Football'));
  assert(true, 'A sees B\'s new event');

  // --- both edit while offline, then come back ---
  repo.down.add('A'); repo.down.add('B');
  await addEvent(pa, {title:'Haircut', who:'a', date:day(4), from:'10:00'});
  await pa.waitForFunction(()=>document.querySelector('#sync').textContent.includes('Waiting'));
  assert(true, 'A shows Waiting while offline');
  await pb.locator('.ev', {hasText:'Gym'}).first().click();
  await pb.fill('#f-title', 'Gym (legs)');
  await pb.click('[data-act="saveEv"]');
  await pb.locator('.ev', {hasText:'Dentist'}).first().click();
  await pb.click('[data-act="delEv"]');
  assert((await pb.textContent('[data-act="delEv"]')).includes('Tap again'), 'delete asks for a second tap');
  await pb.click('[data-act="delEv"]');
  await pa.waitForTimeout(1500);
  repo.down.clear();
  await pa.evaluate(()=>sync()); await pa.waitForFunction(()=>!JSON.parse(localStorage.schedule_local).dirty);
  repo.raceOnce = true;   // B's write collides once
  await pb.evaluate(()=>sync()); await pb.waitForFunction(()=>!JSON.parse(localStorage.schedule_local).dirty && document.querySelector('#sync').textContent.includes('Synced'));
  await pa.evaluate(()=>sync()); await pa.waitForTimeout(500);
  for(const [t,p] of [['A',pa],['B',pb]]){
    const v = await p.textContent('#view');
    assert(v.includes('Haircut') && v.includes('Gym (legs)') && !v.includes('Dentist'), t + ' has merged offline edits from both phones');
  }
  const ev = remoteEvents();
  assert(ev.find(e=>e.title==='Haircut') && ev.find(e=>e.title==='Gym (legs)') && ev.some(e=>e.del), 'repo has both edits and the delete tombstone');
  assert(!ev.some(e=>e.title==='Dentist'), 'deleted event is gone from the repo');

  // names/colours sync
  await pb.click('#gear');
  await pb.click('[data-act="color"][data-p="b"][data-v="#4cc38a"]');
  await pb.waitForFunction(()=>!JSON.parse(localStorage.schedule_local).dirty);
  await pa.evaluate(()=>sync()); await pa.waitForTimeout(400);
  assert(await pa.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--pb').trim()) === '#4cc38a', 'colour change reaches the other phone');
  await pb.goBack();

  console.log('commits:', repo.commits);

  // desktop month view
  const D = await browser.newContext({viewport:{width:1280,height:900}});
  await fakeGitHub(D,'D');
  const pd = await D.newPage();
  await pd.goto(link); await pd.waitForSelector('#w-a');
  await pd.click('[data-seg="me"] [data-v="a"]'); await pd.click('[data-act="welcomeDone"]');
  await pd.click('[data-tab="month"]');
  await pd.screenshot({path:SHOTS+'/08-desktop-month.png'});
  await pa.click('[data-tab="up"]');
  await pa.screenshot({path:SHOTS+'/09-upcoming-after-sync.png'});
  await pa.click('[data-tab="month"]');
  await pa.screenshot({path:SHOTS+'/10-month-after-sync.png'});

  // offline app shell: service worker serves the page with the server gone
  await pa.waitForTimeout(500);
  const swOk = await pa.evaluate(()=>navigator.serviceWorker.ready.then(()=>true));
  assert(swOk, 'service worker active');

  console.log(errs.length ? 'PAGE ERRORS:\n' + errs.join('\n') : 'no page errors');
  console.log(fail ? fail + ' FAILED' : 'ALL PASSED');
  console.log('screenshots in', SHOTS);
  await browser.close(); srv.close();
  process.exit(fail || errs.length ? 1 : 0);
})().catch(e=>{ console.error(e); process.exit(1); });
