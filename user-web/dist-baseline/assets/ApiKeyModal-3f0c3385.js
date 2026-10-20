import{d as C,j as u,k as g,q as a,M as r,u as t,B as i,C as n,r as k,b as E,x as h,L as A,v,O as I,F as $,af as B,w as H}from"./vendor-vue-615fea44.js";import{t as e,u as V,a as P,e as Q}from"./index-c1ef5679.js";import{k as D,B as K,l as Y,u as j,X as J,ak as M,K as R,N as q,e as G,U as Z,h as ee,a2 as te,a as ae}from"./vendor-naive-b90d62ec.js";import{s as O}from"./sanitize-921be1ad.js";const se={class:"border dark:border-gray-700 rounded-lg p-4"},re={class:"font-medium mb-3"},ne={class:"text-sm text-gray-500 mb-2"},oe={style:{"min-width":"120px"}},ie=C({__name:"ApiDocCommon",setup(l){return(y,s)=>(u(),g("div",se,[a("h4",re,r(t(e)("extApi.docCommonParams")),1),i(t(Y),null,{default:n(()=>[i(t(D),{title:t(e)("extApi.docRequest"),name:"request"},{default:n(()=>[i(t(K),{bordered:!0,"single-line":!1,size:"small"},{default:n(()=>s[0]||(s[0]=[a("tbody",null,[a("tr",null,[a("td",{style:{width:"150px","font-weight":"600"}}," Authorization "),a("td",null,"Bearer YOUR_API_KEY")]),a("tr",null,[a("td",{style:{"font-weight":"600"}}," Content-Type "),a("td",null,"application/json")])],-1)])),_:1})]),_:1},8,["title"]),i(t(D),{title:t(e)("extApi.docResponseStructure"),name:"response-structure"},{default:n(()=>[a("p",ne,r(t(e)("extApi.docResponseStructureDesc")),1),i(t(K),{bordered:!0,"single-line":!1,size:"small",style:{"margin-bottom":"8px"}},{default:n(()=>[a("thead",null,[a("tr",null,[a("th",oe,r(t(e)("extApi.docParamName")),1),s[1]||(s[1]=a("th",{style:{"min-width":"60px"}}," Type ",-1)),a("th",null,r(t(e)("extApi.docParamDesc")),1)])]),a("tbody",null,[a("tr",null,[s[2]||(s[2]=a("td",null,"success",-1)),s[3]||(s[3]=a("td",null,"boolean",-1)),a("td",null,r(t(e)("extApi.docResponseSuccess")),1)]),a("tr",null,[s[4]||(s[4]=a("td",null,"code",-1)),s[5]||(s[5]=a("td",null,"string",-1)),a("td",null,r(t(e)("extApi.docResponseCode")),1)]),a("tr",null,[s[6]||(s[6]=a("td",null,"message",-1)),s[7]||(s[7]=a("td",null,"string",-1)),a("td",null,r(t(e)("extApi.docResponseMessage")),1)]),a("tr",null,[s[8]||(s[8]=a("td",null,"data",-1)),s[9]||(s[9]=a("td",null,"object",-1)),a("td",null,r(t(e)("extApi.docResponseData")),1)])])]),_:1}),s[10]||(s[10]=a("pre",{class:"text-xs whitespace-pre-wrap break-all bg-gray-100 dark:bg-gray-800 rounded p-3"},`{
  "success": true,
  "code": "success",
  "message": "",
  "data": { ... }
}`,-1))]),_:1},8,["title"])]),_:1})]))}});function le(l){return{endpoints:[{title:e("extApi.docChatTitle"),description:e("extApi.docChatDesc"),endpoint:`${l}/character`,method:"POST",params:[{name:"query",type:"string",required:e("common.yes"),desc:e("extApi.docParamQuery")},{name:"model",type:"string",required:e("common.no"),desc:e("extApi.docParamModel")},{name:"response_mode",type:"string",required:e("common.no"),desc:e("extApi.docParamMode")}],responseFields:[],responseExample:`{
  "success": true,
  "data": {
    "message_id": "a1b2c3d4e5f6",
    "answer": "Hello! How can I help you today?",
    "usage": {
      "prompt_tokens": 10,
      "completion_tokens": 8,
      "total_tokens": 18
    }
  }
}`,curlExample:`curl -X POST '${l}/character' \\
  -H 'Authorization: YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "query": "Hello",
    "model": "gpt-5.4-mini",
    "response_mode": "streaming"
  }'`}]}}function de(l){return{endpoints:[{title:e("extApi.docKbTitle"),description:e("extApi.docKbDesc"),endpoint:`${l}/knowledge`,method:"POST",params:[{name:"query",type:"string",required:e("common.yes"),desc:e("extApi.docParamQuery")},{name:"model",type:"string",required:e("common.no"),desc:e("extApi.docParamModel")},{name:"response_mode",type:"string",required:e("common.no"),desc:e("extApi.docParamMode")}],responseFields:[],responseExample:`{
  "success": true,
  "data": {
    "message_id": "29bf13e7e966489aa68c5d5b44f55d21",
    "answer": "RAG stands for Retrieval-Augmented Generation.",
    "usage": {
      "prompt_tokens": 11,
      "completion_tokens": 230,
      "total_tokens": 241
    }
  }
}`,curlExample:`curl -X POST '${l}/knowledge' \\
  -H 'Authorization: YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "query": "What is RAG?",
    "model": "gpt-5.4-mini",
    "response_mode": "streaming"
  }'`}]}}const pe={1:"Text",2:"Number",3:"Options",4:"Files",5:"Bool"};function ue(l,y){const s=y&&y.length>0,T=s?`<pre class="text-xs m-0" style="white-space:pre-wrap">${y.map(x=>`{
  "name": "${x.name}",
  "content": {
    "type": ${x.type},  // ${pe[x.type]??""}
    "value": ""
  }${x.required?`  // ${e("extApi.docRequired")}`:""}
}`).join(`
`)}</pre>`:e("extApi.docParamInputs"),w=s?y.map((x,b)=>`      {
        "name": "${x.name}",
        "content": {
          "type": ${x.type},
          "value": ""
        }
      }${b<y.length-1?",":""}`).join(`
`):`      {
        "name": "text",
        "content": {
          "type": 1,
          "value": "Analyze this content"
        }
      }`;return{endpoints:[{title:e("extApi.docWfTitle"),description:e("extApi.docWfDesc"),endpoint:`${l}/workflow/run`,method:"POST",params:[{name:"inputs",type:"array",required:e("common.no"),desc:T},{name:"response_mode",type:"string",required:e("common.no"),desc:e("extApi.docParamMode")}],responseFields:[{name:"task_id",type:"string",required:"",desc:e("extApi.docRespTaskId")},{name:"status",type:"string",required:"",desc:e("extApi.docRespStatus")},{name:"outputs",type:"object",required:"",desc:e("extApi.docParamOutputs")}],responseExample:`{
  "success": true,
  "data": {
    "task_id": "f8e7d6c5b4a3",
    "status": "completed",
    "outputs": {
      "<param_name>": {
        "type": 1,
        "title": "<param_title>",
        "value": "Analysis completed successfully"
      }
    }
  }
}`,curlExample:`curl -X POST '${l}/workflow/run' \\
  -H 'Authorization: YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "inputs": [
${w}
    ],
    "response_mode": "blocking"
  }'`}]}}function ce(l){return{endpoints:[{title:e("extApi.docDrawTitle"),description:e("extApi.docDrawDesc"),endpoint:`${l}/draw/generation`,method:"POST",params:[{name:"prompt",type:"string",required:e("common.yes"),desc:e("extApi.docParamPrompt")},{name:"negative_prompt",type:"string",required:e("common.no"),desc:e("extApi.docParamNegativePrompt")},{name:"size",type:"string",required:e("common.no"),desc:e("extApi.docParamSize")},{name:"quality",type:"string",required:e("common.no"),desc:e("extApi.docParamQuality")},{name:"number",type:"integer",required:e("common.no"),desc:e("extApi.docParamNumber")},{name:"model",type:"string",required:e("common.no"),desc:e("extApi.docParamModel")},{name:"seed",type:"integer",required:e("common.no"),desc:e("extApi.docParamSeed")}],responseFields:[{name:"uuid",type:"string",required:"",desc:e("extApi.docDrawUuid")}],responseExample:`{
  "success": true,
  "data": {
    "uuid": "abc123def456"
  }
}`,curlExample:`curl -X POST '${l}/draw/generation' \\
  -H 'Authorization: YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "prompt": "a cute cat playing piano",
    "model": "gpt-image-2",
    "number": 1
  }'`},{title:e("extApi.docDrawDetailTitle"),description:e("extApi.docDrawDetailDesc"),endpoint:`${l}/draw/{uuid}`,method:"GET",params:[{name:"uuid",type:"string",required:e("common.yes"),desc:e("extApi.docDrawDetailUuid")}],responseFields:[{name:"uuid",type:"string",required:"",desc:e("extApi.docDrawUuid")},{name:"processStatus",type:"integer",required:"",desc:e("extApi.docDrawProcessStatus")},{name:"processStatusRemark",type:"string",required:"",desc:e("extApi.docDrawProcessStatusRemark")},{name:"prompt",type:"string",required:"",desc:e("extApi.docDrawPrompt")},{name:"generatedImages",type:"string",required:"",desc:e("extApi.docDrawGeneratedImages")}],responseExample:`{
  "success": true,
  "data": {
    "uuid": "abc123def456",
    "processStatus": 3,
    "processStatusRemark": "",
    "prompt": "a cute cat playing piano",
    "generatedImages": "file-uuid-1,file-uuid-2"
  }
}`,curlExample:`curl -X GET '${l}/draw/abc123def456' \\
  -H 'Authorization: YOUR_API_KEY'`}]}}function me(l){return{endpoints:[{title:e("extApi.docMcpSupportedTitle"),description:e("extApi.docMcpSupportedDesc"),endpoint:`${l}/mcp/supported`,method:"GET",params:[],responseFields:[{name:"uuid",type:"string",required:"",desc:e("extApi.docMcpUuid")},{name:"title",type:"string",required:"",desc:e("extApi.docMcpTitle")},{name:"transportType",type:"string",required:"",desc:e("extApi.docMcpTransportType")},{name:"sseUrl",type:"string",required:"",desc:e("extApi.docMcpSseUrl")},{name:"remark",type:"string",required:"",desc:e("extApi.docMcpRemark")}],responseExample:`{
  "success": true,
  "data": [
    {
      "uuid": "abc123",
      "title": "Web Search",
      "transportType": "sse",
      "sseUrl": "https://example.com/mcp/sse",
      "remark": "Web search tool"
    }
  ]
}`,curlExample:`curl -X GET '${l}/mcp/supported' \\
  -H 'Authorization: YOUR_API_KEY'`},{title:e("extApi.docMcpActiveTitle"),description:e("extApi.docMcpActiveDesc"),endpoint:`${l}/mcp/active`,method:"GET",params:[],responseFields:[{name:"uuid",type:"string",required:"",desc:e("extApi.docMcpUuid")},{name:"title",type:"string",required:"",desc:e("extApi.docMcpTitle")},{name:"transportType",type:"string",required:"",desc:e("extApi.docMcpTransportType")},{name:"isEnable",type:"boolean",required:"",desc:e("extApi.docMcpIsEnable")}],responseExample:`{
  "success": true,
  "data": [
    {
      "uuid": "abc123",
      "title": "Web Search",
      "transportType": "sse",
      "isEnable": true
    }
  ]
}`,curlExample:`curl -X GET '${l}/mcp/active' \\
  -H 'Authorization: YOUR_API_KEY'`}]}}const ye=["id"],xe={class:"font-medium"},ge={key:0,class:"text-sm text-gray-500 mt-0 mb-1"},fe={class:"px-2 py-0.5 bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 rounded text-xs font-medium"},Ae={class:"text-xs text-gray-500"},we={style:{"min-width":"120px"}},_e=["innerHTML"],he={style:{"min-width":"120px"}},ve=["innerHTML"],be={class:"text-xs whitespace-pre-wrap break-all bg-gray-100 dark:bg-gray-800 rounded p-3"},ke={class:"text-xs whitespace-pre-wrap break-all bg-gray-100 dark:bg-gray-800 rounded p-3"},Te=C({__name:"ApiDocPanel",props:{type:{},uuid:{},wfInputDefs:{}},setup(l){const y=l,s=j(),T=k(-1),w=E(()=>{const d="/api".replace(/\/$/,"");return`${window.location.origin}${d}/ext/v1`}),x=E(()=>{const c=w.value;return y.type==="character"?le(c):y.type==="knowledge"?de(c):y.type==="draw"?ce(c):y.type==="mcp"?me(c):ue(c,y.wfInputDefs)}),b=E(()=>x.value.endpoints.length>1);function f(c,d){navigator.clipboard.writeText(d).then(()=>{T.value=c,setTimeout(()=>T.value=-1,2e3)}).catch(()=>s.error(e("common.wrong")))}return(c,d)=>(u(),h(t(G),{vertical:"",size:12},{default:n(()=>[i(t(J),{style:{margin:"4px 0"}}),i(t(M),{style:{margin:"0"}},{default:n(()=>[A(r(t(e)("extApi.docTitle")),1)]),_:1}),i(ie),b.value?(u(),h(t(M),{key:0,style:{margin:"0"}},{default:n(()=>[A(r(t(e)("extApi.docEndpointList")),1)]),_:1})):v("",!0),(u(!0),g($,null,I(x.value.endpoints,(p,_)=>(u(),g("div",{id:`endpoint-${_}`,key:_,class:"border dark:border-gray-700 rounded-lg p-4"},[i(t(M),{style:{margin:"0 0 4px 0"}},{default:n(()=>[a("span",xe,r(p.title),1)]),_:2},1024),p.description?(u(),g("p",ge,r(p.description),1)):v("",!0),i(t(R),{align:"center",size:6,class:"mb-3"},{default:n(()=>[a("span",fe,r(p.method),1),a("code",Ae,r(p.endpoint),1)]),_:2},1024),i(t(Y),null,{default:n(()=>[p.params.length>0?(u(),h(t(D),{key:0,title:t(e)("extApi.docParams"),name:"params"},{default:n(()=>[i(t(K),{bordered:!0,"single-line":!1,size:"small"},{default:n(()=>[a("thead",null,[a("tr",null,[a("th",we,r(t(e)("extApi.docParamName")),1),d[0]||(d[0]=a("th",{style:{"min-width":"60px"}}," Type ",-1)),d[1]||(d[1]=a("th",{style:{"min-width":"70px"}}," Required ",-1)),a("th",null,r(t(e)("extApi.docParamDesc")),1)])]),a("tbody",null,[(u(!0),g($,null,I(p.params,m=>(u(),g("tr",{key:m.name},[a("td",null,r(m.name),1),a("td",null,r(m.type),1),a("td",null,r(m.required),1),a("td",{innerHTML:t(O)(m.desc)},null,8,_e)]))),128))])]),_:2},1024)]),_:2},1032,["title"])):v("",!0),i(t(D),{title:t(e)("extApi.docResponse"),name:"response"},{default:n(()=>[p.responseFields.length>0?(u(),h(t(K),{key:0,bordered:!0,"single-line":!1,size:"small",style:{"margin-bottom":"8px"}},{default:n(()=>[a("thead",null,[a("tr",null,[a("th",he,r(t(e)("extApi.docParamName")),1),d[2]||(d[2]=a("th",{style:{"min-width":"60px"}}," Type ",-1)),a("th",null,r(t(e)("extApi.docParamDesc")),1)])]),a("tbody",null,[(u(!0),g($,null,I(p.responseFields,m=>(u(),g("tr",{key:m.name},[a("td",null,r(m.name),1),a("td",null,r(m.type),1),a("td",{innerHTML:t(O)(m.desc)},null,8,ve)]))),128))])]),_:2},1024)):v("",!0),a("pre",be,r(p.responseExample),1)]),_:2},1032,["title"]),i(t(D),{name:"curl"},{header:n(()=>[i(t(R),{align:"center",justify:"space-between",size:8,style:{width:"100%"}},{default:n(()=>[d[3]||(d[3]=a("span",null,"cURL",-1)),i(t(q),{size:"tiny",onClick:B(m=>f(_,p.curlExample),["stop"])},{default:n(()=>[A(r(T.value===_?t(e)("chat.copied"):t(e)("chat.copy")),1)]),_:2},1032,["onClick"])]),_:2},1024)]),default:n(()=>[a("pre",ke,r(p.curlExample),1)]),_:2},1024)]),_:2},1024)],8,ye))),128))]),_:1}))}}),Pe={key:2},qe={class:"text-xs text-gray-500 mt-2"},Ee=C({__name:"ApiKeyModal",props:{show:{type:Boolean,default:!1},type:{default:""},uuid:{default:""},title:{default:""},wfInputDefs:{}},emits:["update:show"],setup(l,{emit:y}){const s=l,T=y,w=j(),x=V(),b=k(!1),f=k(!1),c=k(""),d=k(""),p=k(!1),_=k(!1),m=k(!1),S=E(()=>["draw","mcp"].includes(s.type)),z=E(()=>!!x.token);H(()=>s.show,o=>{b.value=o,o&&z.value&&F()}),H(()=>b.value,o=>{o||(T("update:show",!1),d.value="",p.value=!1,_.value=!1)});async function F(){if(!f.value){f.value=!0;try{let o;S.value?o=(await P.extApiKeyUserInfo(s.type)).data:o=(await P.extApiKeyInfo(s.type,s.uuid)).data,c.value=(o==null?void 0:o.maskedKey)??"",m.value=(o==null?void 0:o.canManage)??!1,d.value="",p.value=!1,_.value=!1}catch{c.value="",d.value="",w.error(e("common.wrong"))}finally{f.value=!1}}}async function U(){f.value=!0;try{let o;S.value?o=(await P.extApiKeyUserGenerate(s.type)).data:o=(await P.extApiKeyGenerate(s.type,s.uuid)).data,c.value=o.maskedKey,d.value=o.rawKey,_.value=!0,p.value=!0}catch{w.error(e("common.wrong"))}finally{f.value=!1}}async function L(){if(p.value){p.value=!1,d.value="";return}f.value=!0;try{let o;S.value?o=(await P.extApiKeyUserReveal(s.type)).data:o=(await P.extApiKeyReveal(s.type,s.uuid)).data,d.value=o.rawKey,p.value=!0}catch{w.error(e("common.wrong"))}finally{f.value=!1}}async function W(){if(d.value)try{await navigator.clipboard.writeText(d.value),w.success(e("extApi.copySuccess"))}catch{w.error(e("common.wrong"))}}return(o,N)=>(u(),h(t(ae),{show:b.value,"onUpdate:show":N[0]||(N[0]=X=>b.value=X),title:`${o.title} - ${t(e)("extApi.apiAccess")}`,style:{width:"90%","max-width":"900px"},preset:"card","mask-closable":!1,"auto-focus":!1,"content-style":"max-height: 80vh; overflow-y: auto;"},{default:n(()=>[i(t(G),{vertical:"",size:16},{default:n(()=>[z.value?(u(),g($,{key:0},[m.value?(u(),h(t(M),{key:0,style:{margin:"0"}},{default:n(()=>[A(r(t(e)("extApi.apiKeyLabel")),1)]),_:1})):v("",!0),_.value?(u(),h(t(Z),{key:1,type:"warning","show-icon":!0},{default:n(()=>[A(r(t(e)("extApi.closeWarning")),1)]),_:1})):v("",!0),m.value?(u(),g("div",Pe,[c.value?(u(),g($,{key:1},[i(t(ee),{value:p.value?d.value:c.value,readonly:"",style:{backgroundColor:"var(--n-color-disabled)",marginBottom:"8px"}},null,8,["value"]),i(t(R),{justify:"end",size:8},{default:n(()=>[p.value?(u(),h(t(q),{key:0,size:"small",onClick:W},{default:n(()=>[A(r(t(e)("extApi.copyKey")),1)]),_:1})):v("",!0),i(t(q),{size:"small",onClick:L},{default:n(()=>[A(r(p.value?t(e)("extApi.hideKey"):t(e)("extApi.viewKey")),1)]),_:1}),i(t(te),{onPositiveClick:U},{trigger:n(()=>[i(t(q),{size:"small",type:"warning"},{default:n(()=>[A(r(t(e)("extApi.regenerateKey")),1)]),_:1})]),default:n(()=>[A(" "+r(t(e)("extApi.regenerateConfirm")),1)]),_:1})]),_:1}),a("p",qe,r(t(e)("extApi.usageTip")),1)],64)):(u(),h(t(R),{key:0,justify:"center",size:12},{default:n(()=>[i(t(q),{type:"primary",loading:f.value,onClick:U},{default:n(()=>[A(r(t(e)("extApi.generateKey")),1)]),_:1},8,["loading"])]),_:1}))])):v("",!0)],64)):v("",!0),i(Te,{type:s.type,uuid:s.uuid,"wf-input-defs":s.wfInputDefs},null,8,["type","uuid","wf-input-defs"])]),_:1})]),_:1},8,["show","title"]))}}),Re=Q(Ee,[["__scopeId","data-v-5666c95f"]]);export{Re as A};
