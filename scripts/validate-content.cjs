#!/usr/bin/env node
const fs = require('node:fs');
const data = JSON.parse(fs.readFileSync('docs/feed/daily.json','utf8'));
function fail(s){throw Error('Invalid Folio feed: '+s)}
if(data.schemaVersion!==1 || !Array.isArray(data.items) || data.items.length>12) fail('schema');
if(!data.updatedAt || !Number.isFinite(Date.parse(data.updatedAt))) fail('updatedAt');
const urls = new Set();
for(const [index,item] of data.items.entries()){
 if(!item || typeof item!=='object') fail('article '+index);
 for(const key of ['title','url','source','summary','date']){
   if(typeof item[key]!=='string' || !item[key].trim()) fail(key+' at '+index);
 }
 const url = new URL(item.url);
 if(url.protocol!=='https:' || url.username || url.password || url.hostname==='example.org' || urls.has(item.url)) fail('URL '+index);
 urls.add(item.url);
 if(item.title.length>400 || item.source.length>90 || item.summary.length>1000) fail('length '+index);
 if(!Number.isFinite(Date.parse(item.date)) || Date.parse(item.date)>Date.now()+86400000) fail('date '+index);
 if(item.editorNote && (typeof item.editorNote!=='string' || item.editorNote.length>1200)) fail('editorNote');
 if(item.vocabulary){
  if(!Array.isArray(item.vocabulary) || item.vocabulary.length>12) fail('vocabulary');
  for(const v of item.vocabulary){
   if(!v || !/^[A-Za-z][A-Za-z'-]{1,31}$/.test(v.word||'') || typeof v.meaning!=='string'||v.meaning.length>100) fail('word');
  }
 }
}
console.log('Validated',data.items.length,'Folio daily stories');
