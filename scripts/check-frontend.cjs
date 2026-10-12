#!/usr/bin/env node
'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const html=fs.readFileSync('docs/index.html','utf8');
const css=fs.readFileSync('docs/style.css','utf8');
const js=fs.readFileSync('docs/app.js','utf8');
assert.match(html,/app\.js\?v=4/,'HTML must reference the current app bundle');
assert.match(html,/style\.css\?v=4/,'HTML must reference the current design system');
assert.match(html,/id="main"/,'Missing app mount');
assert.match(html,/id="wordOverlay"/,'Missing vocabulary dialog');
assert.match(html,/id="nav"/,'Missing accessible navigation');
new vm.Script(js,{filename:'docs/app.js'});
for(const feature of ['function reader()', 'function wordsPage()', 'function profile()', 'function backupPage()', 'async function refresh()', 'function handleFeeds(', "case 'quiz-answer'", "case 'restore-pasted'"]){
 assert.ok(js.includes(feature),'Missing app path: '+feature);
}
for(const style of ['.learning-block','.journal-item','.vocab-row','.bottom-nav']){
 assert.ok(css.includes(style),'Missing design rules: '+style);
}
const issue=JSON.parse(fs.readFileSync('docs/feed/daily.json','utf8'));
assert.equal(issue.schemaVersion,1);
assert.ok(issue.items.every(x=>x.readingText&&x.vocabulary?.length>=3&&x.discussionQuestion));
console.log('Folio frontend source, navigation, learning flows and issue schema verified');
