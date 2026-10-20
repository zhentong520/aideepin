import{H as kg,t as Qs,s as Uf,V as Cg,a as Ud,D as Sg,R as zg,m as vi,b as Og,G as Mg,c as $g,d as Xc,e as Sn,f as Je,i as wo,l as Kn,h as Mt,p as Sr,j as _f,k as Df,n as fl,o as Ca,q as fi,r as bo,u as et,v as Eg,w as Ll,x as Ag,E as ns,y as _d,z as Rg,A as Tg,B as Pg,C as Ig,F as qg,I as Ng,J as Ug,K as _g,L as Dg,M as Fg,N as Lg,O as Jg,P as Bg,Q as Wg,S as Vg,T as Hg,U as Yg,W as Kg,X as Qg,Y as Gg,Z as Xg,_ as Zg,$ as em,a0 as nm,a1 as ts,a2 as tm,a3 as om,a4 as ys,a5 as Dd,a6 as mc,a7 as Fd,a8 as rm,a9 as am,aa as ws,ab as ot,ac as sm,ad as im,ae as lm,af as Gs,ag as cm,ah as zr,ai as Ff,aj as Or,ak as um,al as Jl,am as pl,an as hl,ao as Fu,ap as Lu,aq as Ju,ar as pa,as as Oo,at as Lr,au as dm,av as vm,aw as fm,ax as pm,ay as ps,az as Lf,aA as Jf,aB as hs,aC as ha,aD as Bf,aE as Wf,aF as hm,aG as Zc,aH as Ji,aI as cs,aJ as gm,aK as mm,aL as Vf,aM as bm,aN as jm,aO as Hf,aP as Yf,aQ as Hr,aR as eu,aS as gs,aT as xm,aU as ym,aV as Yr,aW as wm,aX as km,aY as Cm,aZ as _a,a_ as Sm,a$ as Bi,b0 as Ld,b1 as Bu,b2 as zm,b3 as gl,b4 as Om,b5 as Mm,b6 as $m,b7 as Kf,b8 as Em,b9 as Am,ba as Rm,bb as Qf,bc as Jd,bd as Tm,be as Pm,bf as Im}from"./vendor-bd10311e.js";import{d as qm,a as Nm,r as bc,W as Um,b as Bd,A as _m,f as Dm,w as jc,u as Fm,h as Lm}from"./vendor-vue-d219d204.js";const Jm="modulepreload",Bm=function(e){return"/"+e},Wd={},Wm=function(n,o,r){if(!o||o.length===0)return n();const s=document.getElementsByTagName("link");return Promise.all(o.map(a=>{if(a=Bm(a),a in Wd)return;Wd[a]=!0;const i=a.endsWith(".css"),l=i?'[rel="stylesheet"]':"";if(!!r)for(let u=s.length-1;u>=0;u--){const h=s[u];if(h.href===a&&(!i||h.rel==="stylesheet"))return}else if(document.querySelector(`link[href="${a}"]${l}`))return;const d=document.createElement("link");if(d.rel=i?"stylesheet":Jm,i||(d.as="script",d.crossOrigin=""),d.href=a,document.head.appendChild(d),i)return new Promise((u,h)=>{d.addEventListener("load",u),d.addEventListener("error",()=>h(new Error(`Unable to preload CSS for ${a}`)))})})).then(()=>n()).catch(a=>{const i=new Event("vite:preloadError",{cancelable:!0});if(i.payload=a,window.dispatchEvent(i),!i.defaultPrevented)throw a})};var Vm=["mainAxis","crossAxis","fallbackPlacements","fallbackStrategy","fallbackAxisSideDirection","flipAlignment"],Hm=["mainAxis","crossAxis","limiter"];function Gf(e,n){if(e==null)return{};var o,r,s=function(i,l){if(i==null)return{};var c={};for(var d in i)if({}.hasOwnProperty.call(i,d)){if(l.indexOf(d)!==-1)continue;c[d]=i[d]}return c}(e,n);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(r=0;r<a.length;r++)o=a[r],n.indexOf(o)===-1&&{}.propertyIsEnumerable.call(e,o)&&(s[o]=e[o])}return s}function Vd(e,n){var o=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);n&&(r=r.filter(function(s){return Object.getOwnPropertyDescriptor(e,s).enumerable})),o.push.apply(o,r)}return o}function me(e){for(var n=1;n<arguments.length;n++){var o=arguments[n]!=null?arguments[n]:{};n%2?Vd(Object(o),!0).forEach(function(r){Ym(e,r,o[r])}):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(o)):Vd(Object(o)).forEach(function(r){Object.defineProperty(e,r,Object.getOwnPropertyDescriptor(o,r))})}return e}function Ym(e,n,o){return(n=function(r){var s=function(a,i){if(typeof a!="object"||!a)return a;var l=a[Symbol.toPrimitive];if(l!==void 0){var c=l.call(a,i||"default");if(typeof c!="object")return c;throw new TypeError("@@toPrimitive must return a primitive value.")}return(i==="string"?String:Number)(a)}(r,"string");return typeof s=="symbol"?s:s+""}(n))in e?Object.defineProperty(e,n,{value:o,enumerable:!0,configurable:!0,writable:!0}):e[n]=o,e}function Hd(e,n,o,r,s,a,i){try{var l=e[a](i),c=l.value}catch(d){return void o(d)}l.done?n(c):Promise.resolve(c).then(r,s)}function bn(e){return function(){var n=this,o=arguments;return new Promise(function(r,s){var a=e.apply(n,o);function i(c){Hd(a,r,s,i,l,"next",c)}function l(c){Hd(a,r,s,i,l,"throw",c)}i(void 0)})}}var Yd,xc,Kd,yc;typeof window<"u"&&((xc=(Yd=(yc=(Kd=window).__svelte)!==null&&yc!==void 0?yc:Kd.__svelte={}).v)!==null&&xc!==void 0?xc:Yd.v=new Set).add("5");var Rs=!1;Rs=!0;var Fo=Symbol(),Km=!1,Bl=32,Xf=64,wc=128,Mr=256,nu=512,Xo=1024,Ts=2048,Ps=4096,ja=8192,Wu=16384,Ai=65536,Qm=1<<17,Gm=1<<20,tu=1<<21,xa=Symbol("$state"),Zf=Symbol("legacy props"),Xm=Symbol(""),Ri=Array.isArray,Zm=Array.prototype.indexOf,ou=Array.from,eb=Object.defineProperty,Kr=Object.getOwnPropertyDescriptor,ep=Object.getOwnPropertyDescriptors,nb=Object.prototype,tb=Array.prototype,Vu=Object.getPrototypeOf,Qd=Object.isExtensible;function Xs(e){return typeof e=="function"}var Gd=()=>{};function ob(e){return e()}function ml(e){for(var n=0;n<e.length;n++)e[n]()}var pi=[],kc=[];function np(){var e=pi;pi=[],ml(e)}function Wl(e){pi.length===0&&queueMicrotask(np),pi.push(e)}function Xd(){var e;pi.length>0&&np(),kc.length>0&&(e=kc,kc=[],ml(e))}function tp(e){return e===this.v}function Hu(e,n){return e!=e?n==n:e!==n||e!==null&&typeof e=="object"||typeof e=="function"}function rb(e,n){return e!==n}function Yu(e){return!Hu(e,this.v)}function Ra(e,n){if(typeof e!="object"||e===null||xa in e)return e;var o=Vu(e);if(o!==nb&&o!==tb)return e;var r=new Map,s=Ri(e),a=Fr(0),i=Nt,l=c=>{var d,u=Nt;return Ar(i),d=c(),Ar(u),d};return s&&r.set("length",Fr(e.length)),new Proxy(e,{defineProperty(c,d,u){"value"in u&&u.configurable!==!1&&u.enumerable!==!1&&u.writable!==!1||function(){throw new Error("https://svelte.dev/e/state_descriptors_fixed")}();var h=r.get(d);return h===void 0?(h=l(()=>Fr(u.value)),r.set(d,h)):v(h,l(()=>Ra(u.value))),!0},deleteProperty(c,d){var u=r.get(d);if(u===void 0)d in c&&r.set(d,l(()=>Fr(Fo)));else{if(s&&typeof d=="string"){var h=r.get("length"),p=Number(d);Number.isInteger(p)&&p<h.v&&v(h,p)}v(u,Fo),Zd(a)}return!0},get(c,d,u){var h;if(d===xa)return e;var p=r.get(d),m=d in c;if(p===void 0&&(!m||(h=Kr(c,d))!==null&&h!==void 0&&h.writable)&&(p=l(()=>Fr(Ra(m?c[d]:Fo))),r.set(d,p)),p!==void 0){var g=t(p);return g===Fo?void 0:g}return Reflect.get(c,d,u)},getOwnPropertyDescriptor(c,d){var u=Reflect.getOwnPropertyDescriptor(c,d);if(u&&"value"in u){var h=r.get(d);h&&(u.value=t(h))}else if(u===void 0){var p=r.get(d),m=p==null?void 0:p.v;if(p!==void 0&&m!==Fo)return{enumerable:!0,configurable:!0,value:m,writable:!0}}return u},has(c,d){var u;if(d===xa)return!0;var h=r.get(d),p=h!==void 0&&h.v!==Fo||Reflect.has(c,d);return(h!==void 0||Xt!==null&&(!p||(u=Kr(c,d))!==null&&u!==void 0&&u.writable))&&(h===void 0&&(h=l(()=>Fr(p?Ra(c[d]):Fo)),r.set(d,h)),t(h)===Fo)?!1:p},set(c,d,u,h){var p,m=r.get(d),g=d in c;if(s&&d==="length")for(var b=u;b<m.v;b+=1){var j=r.get(b+"");j!==void 0?v(j,Fo):b in c&&(j=l(()=>Fr(Fo)),r.set(b+"",j))}m===void 0?(!g||(p=Kr(c,d))!==null&&p!==void 0&&p.writable)&&(v(m=l(()=>Fr(void 0)),l(()=>Ra(u))),r.set(d,m)):(g=m.v!==Fo,v(m,l(()=>Ra(u))));var x=Reflect.getOwnPropertyDescriptor(c,d);if(x!=null&&x.set&&x.set.call(h,u),!g){if(s&&typeof d=="string"){var k=r.get("length"),$=Number(d);Number.isInteger($)&&$>=k.v&&v(k,$+1)}Zd(a)}return!0},ownKeys(c){t(a);var d=Reflect.ownKeys(c).filter(p=>{var m=r.get(p);return m===void 0||m.v!==Fo});for(var[u,h]of r)h.v===Fo||u in c||d.push(u);return d},setPrototypeOf(){(function(){throw new Error("https://svelte.dev/e/state_prototype_fixed")})()}})}function Zd(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1;v(e,e.v+n)}var hi=new Map;function Da(e,n){return{f:0,v:e,reactions:null,equals:tp,rv:0,wv:0}}function Fr(e,n){var o=Da(e);return cp(o),o}function E(e){var n,o,r=arguments.length>1&&arguments[1]!==void 0&&arguments[1],s=Da(e);return r||(s.equals=Yu),Rs&&kt!==null&&kt.l!==null&&((o=(n=kt.l).s)!==null&&o!==void 0?o:n.s=[]).push(s),s}function Ko(e,n){return v(e,Rr(()=>t(e))),n}function v(e,n){var o,r=arguments.length>2&&arguments[2]!==void 0&&arguments[2];return Nt!==null&&!kr&&Us()&&18&Nt.f&&((o=Gr)===null||o===void 0||!o.includes(e))&&function(){throw new Error("https://svelte.dev/e/state_unsafe_mutation")}(),ru(e,r?Ra(n):n)}function ru(e,n){if(!e.equals(n)){var o=e.v;Ti?hi.set(e,n):hi.set(e,o),e.v=n,e.wv=dp(),op(e,Ts),Us()&&Xt!==null&&Xt.f&Xo&&!(96&Xt.f)&&(er===null?function(r){er=r}([e]):er.push(e))}return n}function ev(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1,o=t(e),r=n===1?o++:o--;return v(e,o),r}function op(e,n){var o=e.reactions;if(o!==null)for(var r=Us(),s=o.length,a=0;a<s;a++){var i=o[a],l=i.f;l&Ts||(r||i!==Xt)&&(yr(i,n),1280&l&&(2&l?op(i,Ps):Ql(i)))}}function ks(e){var n=2050,o=Nt!==null&&2&Nt.f?Nt:null;return Xt===null||o!==null&&o.f&Mr?n|=Mr:Xt.f|=Gm,{ctx:kt,deps:null,effects:null,equals:tp,f:n,fn:e,reactions:null,rv:0,v:null,wv:0,parent:o??Xt}}function Wo(e){var n=ks(e);return cp(n),n}function ae(e){var n=ks(e);return n.equals=Yu,n}function rp(e){var n=e.effects;if(n!==null){e.effects=null;for(var o=0;o<n.length;o+=1)Tr(n[o])}}function ap(e){var n=function(o){var r,s=Xt;Sa(function(a){for(var i=a.parent;i!==null;){if(!(2&i.f))return i;i=i.parent}return null}(o));try{rp(o),r=fp(o)}finally{Sa(s)}return r}(e);yr(e,(ga||e.f&Mr)&&e.deps!==null?Ps:Xo),e.equals(n)||(e.v=n,e.wv=dp())}var Qr,sp,ip,lp;function Vl(){var e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:"";return document.createTextNode(e)}function Qo(e){return ip.call(e)}function Hl(e){return lp.call(e)}function M(e,n){return Qo(e)}function rn(e,n){var o=Qo(e);return o instanceof Comment&&o.data===""?Hl(o):o}function q(e){for(var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1,o=e;n--;)o=Hl(o);return o}var Wi=!1,bl=!1,jl=null,Pa=!1,Ti=!1;function nv(e){Ti=e}var li=[],Nt=null,kr=!1;function Ar(e){Nt=e}var Xt=null;function Sa(e){Xt=e}var Gr=null;function cp(e){Nt!==null&&Nt.f&tu&&(Gr===null?Gr=[e]:Gr.push(e))}var Lo=null,Ho=0,er=null,up=1,xl=0,ga=!1;function dp(){return++up}function Is(e){var n=e.f;if(n&Ts)return!0;if(n&Ps){var o=e.deps,r=!!(n&Mr);if(o!==null){var s,a,i=!!(n&nu),l=r&&Xt!==null&&!ga,c=o.length;if(i||l){var d=e,u=d.parent;for(s=0;s<c;s++){var h,p,m;a=o[s],(i||(h=a)===null||h===void 0||(h=h.reactions)===null||h===void 0||!h.includes(d))&&((m=(p=a).reactions)!==null&&m!==void 0?m:p.reactions=[]).push(d)}i&&(d.f^=nu),!l||u===null||u.f&Mr||(d.f^=Mr)}for(s=0;s<c;s++)if(Is(a=o[s])&&ap(a),a.wv>e.wv)return!0}r&&(Xt===null||ga)||yr(e,Xo)}return!1}function Yl(e,n,o,r){if(Wi){if(o===null&&(Wi=!1),function(s){return!(s.f&Wu||s.parent!==null&&s.parent.f&wc)}(n))throw e}else o!==null&&(Wi=!0),function(s,a){for(var i=a;i!==null;){if(i.f&wc)try{return void i.fn(s)}catch{i.f^=wc}i=i.parent}throw Wi=!1,s}(e,n)}function vp(e,n){var o=!(arguments.length>2&&arguments[2]!==void 0)||arguments[2],r=e.reactions;if(r!==null)for(var s=0;s<r.length;s++){var a,i=r[s];(a=Gr)!==null&&a!==void 0&&a.includes(e)||(2&i.f?vp(i,n,!1):n===i&&(o?yr(i,Ts):i.f&Xo&&yr(i,Ps),Ql(i)))}}function fp(e){var n=Lo,o=Ho,r=er,s=Nt,a=ga,i=Gr,l=kt,c=kr,d=e.f;Lo=null,Ho=0,er=null,ga=!!(d&Mr)&&(kr||!Pa||Nt===null),Nt=96&d?null:e,Gr=null,tv(e.ctx),kr=!1,xl++,e.f|=tu;try{var u=(0,e.fn)(),h=e.deps;if(Lo!==null){var p;if(yl(e,Ho),h!==null&&Ho>0)for(h.length=Ho+Lo.length,p=0;p<Lo.length;p++)h[Ho+p]=Lo[p];else e.deps=h=Lo;if(!ga)for(p=Ho;p<h.length;p++){var m,g;((g=(m=h[p]).reactions)!==null&&g!==void 0?g:m.reactions=[]).push(e)}}else h!==null&&Ho<h.length&&(yl(e,Ho),h.length=Ho);if(Us()&&er!==null&&!kr&&h!==null&&!(6146&e.f))for(p=0;p<er.length;p++)vp(er[p],e);return s!==null&&(xl++,er!==null&&(r===null?r=er:r.push(...er))),u}finally{Lo=n,Ho=o,er=r,Nt=s,ga=a,Gr=i,tv(l),kr=c,e.f^=tu}}function ab(e,n){var o=n.reactions;if(o!==null){var r=Zm.call(o,e);if(r!==-1){var s=o.length-1;s===0?o=n.reactions=null:(o[r]=o[s],o.pop())}}o===null&&2&n.f&&(Lo===null||!Lo.includes(n))&&(yr(n,Ps),768&n.f||(n.f^=nu),rp(n),yl(n,0))}function yl(e,n){var o=e.deps;if(o!==null)for(var r=n;r<o.length;r++)ab(e,o[r])}function Kl(e){var n=e.f;if(!(n&Wu)){yr(e,Xo);var o=Xt,r=kt,s=Pa;Xt=e,Pa=!0;try{16&n?function(i){for(var l=i.first;l!==null;){var c=l.next;l.f&Bl||Tr(l),l=c}}(e):bp(e),mp(e);var a=fp(e);e.teardown=typeof a=="function"?a:null,e.wv=up,e.deps}catch(i){Yl(i,e,o,r||e.ctx)}finally{Pa=s,Xt=o}}}function sb(){try{(function(){throw new Error("https://svelte.dev/e/effect_update_depth_exceeded")})()}catch(e){if(jl===null)throw e;Yl(e,jl,null)}}function pp(){var e=Pa;try{var n=0;for(Pa=!0;li.length>0;){n++>1e3&&sb();var o=li,r=o.length;li=[];for(var s=0;s<r;s++)ib(lb(o[s]))}}finally{bl=!1,Pa=e,jl=null,hi.clear()}}function ib(e){var n=e.length;if(n!==0)for(var o=0;o<n;o++){var r=e[o];if(!(24576&r.f))try{Is(r)&&(Kl(r),r.deps===null&&r.first===null&&r.nodes_start===null&&(r.teardown===null?jp(r):r.fn=null))}catch(s){Yl(s,r,null,r.ctx)}}}function Ql(e){bl||(bl=!0,queueMicrotask(pp));for(var n=jl=e;n.parent!==null;){var o=(n=n.parent).f;if(96&o){if(!(o&Xo))return;n.f^=Xo}}li.push(n)}function lb(e){for(var n=[],o=e;o!==null;){var r=o.f,s=!!(96&r);if(!(s&&r&Xo||r&ja)){if(4&r)n.push(o);else if(s)o.f^=Xo;else{var a=Nt;try{Nt=o,Is(o)&&Kl(o)}catch(c){Yl(c,o,null,o.ctx)}finally{Nt=a}}var i=o.first;if(i!==null){o=i;continue}}var l=o.parent;for(o=o.next;o===null&&l!==null;)o=l.next,l=l.parent}return n}function Gt(e){for(Xd();li.length>0;)bl=!0,pp(),Xd()}function hp(){return(hp=bn(function*(){yield Promise.resolve(),Gt()})).apply(this,arguments)}function t(e){var n,o=!!(2&e.f);if(Nt===null||kr){if(o&&e.deps===null&&e.effects===null){var r=e,s=r.parent;s===null||s.f&Mr||(r.f^=Mr)}}else if((n=Gr)===null||n===void 0||!n.includes(e)){var a=Nt.deps;e.rv<xl&&(e.rv=xl,Lo===null&&a!==null&&a[Ho]===e?Ho++:Lo===null?Lo=[e]:ga&&Lo.includes(e)||Lo.push(e))}return o&&Is(r=e)&&ap(r),Ti&&hi.has(e)?hi.get(e):e.v}function Rr(e){var n=kr;try{return kr=!0,e()}finally{kr=n}}var cb=-7169;function yr(e,n){e.f=e.f&cb|n}function O(e){if(typeof e=="object"&&e&&!(e instanceof EventTarget)){if(xa in e)au(e);else if(!Array.isArray(e))for(var n in e){var o=e[n];typeof o=="object"&&o&&xa in o&&au(o)}}}function au(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:new Set;if(!(typeof e!="object"||e===null||e instanceof EventTarget||n.has(e))){for(var o in n.add(e),e instanceof Date&&e.getTime(),e)try{au(e[o],n)}catch{}var r=Vu(e);if(r!==Object.prototype&&r!==Array.prototype&&r!==Map.prototype&&r!==Set.prototype&&r!==Date.prototype){var s=ep(r);for(var a in s){var i=s[a].get;if(i)try{i.call(e)}catch{}}}}}function gp(e){Xt===null&&Nt===null&&function(){throw new Error("https://svelte.dev/e/effect_orphan")}(),Nt!==null&&Nt.f&Mr&&Xt===null&&function(){throw new Error("https://svelte.dev/e/effect_in_unowned_derived")}(),Ti&&function(){throw new Error("https://svelte.dev/e/effect_in_teardown")}()}function qs(e,n,o){var r=!(arguments.length>3&&arguments[3]!==void 0)||arguments[3],s=Xt,a={ctx:kt,deps:null,nodes_start:null,nodes_end:null,f:e|Ts,first:null,fn:n,last:null,next:null,parent:s,prev:null,teardown:null,transitions:null,wv:0};if(o)try{Kl(a),a.f|=32768}catch(c){throw Tr(a),c}else n!==null&&Ql(a);if(!(o&&a.deps===null&&a.first===null&&a.nodes_start===null&&a.teardown===null&&!(1048704&a.f))&&r&&(s!==null&&function(c,d){var u=d.last;u===null?d.last=d.first=c:(u.next=c,c.prev=u,d.last=c)}(a,s),Nt!==null&&2&Nt.f)){var i,l=Nt;((i=l.effects)!==null&&i!==void 0?i:l.effects=[]).push(a)}return a}function Ku(e){var n=qs(8,null,!1);return yr(n,Xo),n.teardown=e,n}function su(e){if(gp(),!(Xt!==null&&Xt.f&Bl&&kt!==null&&!kt.m))return $o(e);var n,o=kt;((n=o.e)!==null&&n!==void 0?n:o.e=[]).push({fn:e,effect:Xt,reaction:Nt})}function $o(e){return qs(4,e,!1)}function F(e,n){var o=kt,r={effect:null,ran:!1};o.l.r1.push(r),r.effect=Ns(()=>{e(),r.ran||(r.ran=!0,v(o.l.r2,!0),Rr(n))})}function mt(){var e=kt;Ns(()=>{if(t(e.l.r2)){for(var n of e.l.r1){var o=n.effect;o.f&Xo&&yr(o,Ps),Is(o)&&Kl(o),n.ran=!1}e.l.r2.v=!1}})}function Ns(e){return qs(8,e,!0)}function be(e){var n=arguments.length>2&&arguments[2]!==void 0?arguments[2]:ks,o=(arguments.length>1&&arguments[1]!==void 0?arguments[1]:[]).map(n);return Ya(()=>e(...o.map(t)))}function Ya(e){return qs(24|(arguments.length>1&&arguments[1]!==void 0?arguments[1]:0),e,!0)}function na(e){return qs(40,e,!0,!(arguments.length>1&&arguments[1]!==void 0)||arguments[1])}function mp(e){var n=e.teardown;if(n!==null){var o=Ti,r=Nt;nv(!0),Ar(null);try{n.call(null)}finally{nv(o),Ar(r)}}}function bp(e){var n=arguments.length>1&&arguments[1]!==void 0&&arguments[1],o=e.first;for(e.first=e.last=null;o!==null;){var r=o.next;o.f&Xf?o.parent=null:Tr(o,n),o=r}}function Tr(e){var n=!(arguments.length>1&&arguments[1]!==void 0)||arguments[1],o=!1;if((n||524288&e.f)&&e.nodes_start!==null){for(var r=e.nodes_start,s=e.nodes_end;r!==null;){var a=r===s?null:Hl(r);r.remove(),r=a}o=!0}bp(e,n&&!o),yl(e,0),yr(e,Wu);var i=e.transitions;if(i!==null)for(var l of i)l.stop();mp(e);var c=e.parent;c!==null&&c.first!==null&&jp(e),e.next=e.prev=e.teardown=e.ctx=e.deps=e.fn=e.nodes_start=e.nodes_end=null}function jp(e){var n=e.parent,o=e.prev,r=e.next;o!==null&&(o.next=r),r!==null&&(r.prev=o),n!==null&&(n.first===e&&(n.first=r),n.last===e&&(n.last=o))}function Cs(e,n){var o=[];Qu(e,o,!0),xp(o,()=>{Tr(e),n&&n()})}function xp(e,n){var o=e.length;if(o>0){var r=()=>--o||n();for(var s of e)s.out(r)}else n()}function Qu(e,n,o){if(!(e.f&ja)){if(e.f^=ja,e.transitions!==null)for(var r of e.transitions)(r.is_global||o)&&n.push(r);for(var s=e.first;s!==null;){var a=s.next;Qu(s,n,!!(s.f&Ai||s.f&Bl)&&o),s=a}}}function wl(e){yp(e,!0)}function yp(e,n){if(e.f&ja){e.f^=ja,e.f&Xo||(e.f^=Xo),Is(e)&&(yr(e,Ts),Ql(e));for(var o=e.first;o!==null;){var r=o.next;yp(o,!!(o.f&Ai||o.f&Bl)&&n),o=r}if(e.transitions!==null)for(var s of e.transitions)(s.is_global||n)&&s.in()}}function Pi(e){throw new Error("https://svelte.dev/e/lifecycle_outside_component")}var kt=null;function tv(e){kt=e}function Ma(e){return wp().get(e)}function en(e){var n=kt={p:kt,c:null,d:!1,e:null,m:!1,s:e,x:null,l:null};Rs&&!(arguments.length>1&&arguments[1]!==void 0&&arguments[1])&&(kt.l={s:null,u:null,r1:[],r2:Da(!1)}),Ku(()=>{n.d=!0})}function nn(e){var n=kt;if(n!==null){e!==void 0&&(n.x=e);var o=n.e;if(o!==null){var r=Xt,s=Nt;n.e=null;try{for(var a=0;a<o.length;a++){var i=o[a];Sa(i.effect),Ar(i.reaction),$o(i.fn)}}finally{Sa(r),Ar(s)}}kt=n.p,n.m=!0}return e||{}}function Us(){return!Rs||kt!==null&&kt.l===null}function wp(e){var n,o;return kt===null&&Pi(),(o=(n=kt).c)!==null&&o!==void 0?o:n.c=new Map(function(r){for(var s=r.p;s!==null;){var a=s.c;if(a!==null)return a;s=s.p}return null}(kt)||void 0)}var ub=["beforeinput","click","change","dblclick","contextmenu","focusin","focusout","input","keydown","keyup","mousedown","mousemove","mouseout","mouseover","mouseup","pointerdown","pointermove","pointerout","pointerover","pointerup","touchend","touchmove","touchstart"],db={formnovalidate:"formNoValidate",ismap:"isMap",nomodule:"noModule",playsinline:"playsInline",readonly:"readOnly",defaultvalue:"defaultValue",defaultchecked:"defaultChecked",srcobject:"srcObject",novalidate:"noValidate",allowfullscreen:"allowFullscreen",disablepictureinpicture:"disablePictureInPicture",disableremoteplayback:"disableRemotePlayback"},vb=["touchstart","touchmove"];function fb(e){return vb.includes(e)}var ov=!1;function kp(e){var n=Nt,o=Xt;Ar(null),Sa(null);try{return e()}finally{Ar(n),Sa(o)}}function pb(e,n,o){var r=arguments.length>3&&arguments[3]!==void 0?arguments[3]:o;e.addEventListener(n,()=>kp(o));var s=e.__on_r;e.__on_r=s?()=>{s(),r(!0)}:()=>r(!0),ov||(ov=!0,document.addEventListener("reset",a=>{Promise.resolve().then(()=>{if(!a.defaultPrevented)for(var i of a.target.elements){var l;(l=i.__on_r)===null||l===void 0||l.call(i)}})},{capture:!0}))}var Cp=new Set,iu=new Set;function Sp(e,n,o){var r=arguments.length>3&&arguments[3]!==void 0?arguments[3]:{};function s(a){if(r.capture||ri.call(n,a),!a.cancelBubble)return kp(()=>o==null?void 0:o.call(this,a))}return e.startsWith("pointer")||e.startsWith("touch")||e==="wheel"?Wl(()=>{n.addEventListener(e,s,r)}):n.addEventListener(e,s,r),s}function ie(e,n,o,r,s){var a={capture:r,passive:s},i=Sp(e,n,o,a);n!==document.body&&n!==window&&n!==document||Ku(()=>{n.removeEventListener(e,i,a)})}function Ii(e){for(var n=0;n<e.length;n++)Cp.add(e[n]);for(var o of iu)o(e)}function ri(e){var n,o=this,r=o.ownerDocument,s=e.type,a=((n=e.composedPath)===null||n===void 0?void 0:n.call(e))||[],i=a[0]||e.target,l=0,c=e.__root;if(c){var d=a.indexOf(c);if(d!==-1&&(o===document||o===window))return void(e.__root=o);var u=a.indexOf(o);if(u===-1)return;d<=u&&(l=d)}if((i=a[l]||e.target)!==o){eb(e,"currentTarget",{configurable:!0,get:()=>i||r});var h=Nt,p=Xt;Ar(null),Sa(null);try{for(var m,g=[];i!==null;){var b=i.assignedSlot||i.parentNode||i.host||null;try{var j=i["__"+s];if(j!=null&&(!i.disabled||e.target===i))if(Ri(j)){var[x,...k]=j;x.apply(i,[e,...k])}else j.call(i,e)}catch(y){m?g.push(y):m=y}if(e.cancelBubble||b===o||b===null)break;i=b}if(m){var $=function(y){queueMicrotask(()=>{throw y})};for(var S of g)$(S);throw m}}finally{e.__root=o,delete e.currentTarget,Ar(h),Sa(p)}}}function Gu(e){var n=document.createElement("template");return n.innerHTML=e,n.content}function Fa(e,n){var o=Xt;o.nodes_start===null&&(o.nodes_start=e,o.nodes_end=n)}function L(e,n){var o,r=!!(1&n),s=!!(2&n),a=!e.startsWith("<!>");return()=>{o===void 0&&(o=Gu(a?e:"<!>"+e),r||(o=Qo(o)));var i=s||sp?document.importNode(o,!0):o.cloneNode(!0);return r?Fa(Qo(i),i.lastChild):Fa(i,i),i}}function $a(e,n){var o,r=arguments.length>2&&arguments[2]!==void 0?arguments[2]:"svg",s=!e.startsWith("<!>"),a=!!(1&n),i="<".concat(r,">").concat(s?e:"<!>"+e,"</").concat(r,">");return()=>{if(!o){var l=Qo(Gu(i));if(a)for(o=document.createDocumentFragment();Qo(l);)o.appendChild(Qo(l));else o=Qo(l)}var c=o.cloneNode(!0);return a?Fa(Qo(c),c.lastChild):Fa(c,c),c}}function zo(){var e=Vl((arguments.length>0&&arguments[0]!==void 0?arguments[0]:"")+"");return Fa(e,e),e}function to(){var e=document.createDocumentFragment(),n=document.createComment(""),o=Vl();return e.append(n,o),Fa(n,o),e}function A(e,n){e!==null&&e.before(n)}function ln(e,n){var o,r=n==null?"":typeof n=="object"?n+"":n;r!==((o=e.__t)!==null&&o!==void 0?o:e.__t=e.nodeValue)&&(e.__t=r,e.nodeValue=r+"")}function hb(e,n){return function(o,r){var{target:s,anchor:a,props:i={},events:l,context:c,intro:d=!0}=r;(function(){if(Qr===void 0){Qr=window,sp=/Firefox/.test(navigator.userAgent);var g=Element.prototype,b=Node.prototype,j=Text.prototype;ip=Kr(b,"firstChild").get,lp=Kr(b,"nextSibling").get,Qd(g)&&(g.__click=void 0,g.__className=void 0,g.__attributes=null,g.__style=void 0,g.__e=void 0),Qd(j)&&(j.__t=void 0)}})();var u=new Set,h=g=>{for(var b=0;b<g.length;b++){var j=g[b];if(!u.has(j)){u.add(j);var x=fb(j);s.addEventListener(j,ri,{passive:x});var k=os.get(j);k===void 0?(document.addEventListener(j,ri,{passive:x}),os.set(j,1)):os.set(j,k+1)}}};h(ou(Cp)),iu.add(h);var p=void 0,m=function(g){var b=qs(Xf,g,!0);return function(){var j=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{};return new Promise(x=>{j.outro?Cs(b,()=>{Tr(b),x(void 0)}):(Tr(b),x(void 0))})}}(()=>{var g=a??s.appendChild(Vl());return na(()=>{c&&(en({}),kt.c=c),l&&(i.$$events=l),p=o(g,i)||{},c&&nn()}),()=>{for(var b of u){s.removeEventListener(b,ri);var j=os.get(b);--j==0?(document.removeEventListener(b,ri),os.delete(b)):os.set(b,j)}var x;iu.delete(h),g!==a&&((x=g.parentNode)===null||x===void 0||x.removeChild(g))}});return lu.set(p,m),p}(e,n)}var os=new Map,lu=new WeakMap;function Q(e,n){var[o,r]=arguments.length>2&&arguments[2]!==void 0?arguments[2]:[0,0],s=e,a=null,i=null,l=Fo,c=!1,d=function(h){c=!0,u(!(arguments.length>1&&arguments[1]!==void 0)||arguments[1],h)},u=(h,p)=>{l!==(l=h)&&(l?(a?wl(a):p&&(a=na(()=>p(s))),i&&Cs(i,()=>{i=null})):(i?wl(i):p&&(i=na(()=>p(s,[o+1,r]))),a&&Cs(a,()=>{a=null})))};Ya(()=>{c=!1,n(d),c||u(null,null)},o>0?Ai:0)}function zp(e,n,o){var r,s=e,a=Fo,i=Us()?rb:Hu;Ya(()=>{i(a,a=n())&&(r&&Cs(r),r=na(()=>o(s)))})}function ho(e,n){return n}function lo(e,n,o,r,s){var a=arguments.length>5&&arguments[5]!==void 0?arguments[5]:null,i=e,l={flags:n,items:new Map,first:null};!(4&n)||(i=e.appendChild(Vl()));var c=null,d=!1,u=ae(()=>{var h=o();return Ri(h)?h:h==null?[]:ou(h)});Ya(()=>{var h=t(u),p=h.length;d&&p===0||(d=p===0,function(m,g,b,j,x,k,$){var S,y,R,N,_,z,W=!!(8&x),X=!!(3&x),H=m.length,ne=g.items,ye=g.first,te=ye,ve=null,Re=[],Ee=[];if(W)for(z=0;z<H;z+=1){var fe;N=k(R=m[z],z),(_=ne.get(N))!==void 0&&((fe=_.a)===null||fe===void 0||fe.measure(),(y??(y=new Set)).add(_))}for(z=0;z<H;z+=1)if(N=k(R=m[z],z),(_=ne.get(N))!==void 0){var Se;if(X&&gb(_,R,z,x),_.e.f&ja&&(wl(_.e),W&&((Se=_.a)===null||Se===void 0||Se.unfix(),(y??(y=new Set)).delete(_))),_!==te){if(S!==void 0&&S.has(_)){if(Re.length<Ee.length){var we,Ne=Ee[0];ve=Ne.prev;var de=Re[0],oe=Re[Re.length-1];for(we=0;we<Re.length;we+=1)rv(Re[we],Ne,b);for(we=0;we<Ee.length;we+=1)S.delete(Ee[we]);la(g,de.prev,oe.next),la(g,ve,de),la(g,oe,Ne),te=Ne,ve=oe,z-=1,Re=[],Ee=[]}else S.delete(_),rv(_,te,b),la(g,_.prev,_.next),la(g,_,ve===null?g.first:ve.next),la(g,ve,_),ve=_;continue}for(Re=[],Ee=[];te!==null&&te.k!==N;)te.e.f&ja||(S??(S=new Set)).add(te),Ee.push(te),te=te.next;if(te===null)continue;_=te}Re.push(_),ve=_,te=_.next}else ve=mb(te?te.e.nodes_start:b,g,ve,ve===null?g.first:ve.next,R,N,z,j,x,$),ne.set(N,ve),Re=[],Ee=[],te=ve.next;if(te!==null||S!==void 0){for(var ce=S===void 0?[]:ou(S);te!==null;)te.e.f&ja||ce.push(te),te=te.next;var an=ce.length;if(an>0){var Mn=4&x&&H===0?b:null;if(W){for(z=0;z<an;z+=1){var Y;(Y=ce[z].a)===null||Y===void 0||Y.measure()}for(z=0;z<an;z+=1){var I;(I=ce[z].a)===null||I===void 0||I.fix()}}(function(ee,P,pe,K){for(var Me=[],V=P.length,T=0;T<V;T++)Qu(P[T].e,Me,!0);var xn=V>0&&Me.length===0&&pe!==null;if(xn){var Qe=pe.parentNode;Qe.textContent="",Qe.append(pe),K.clear(),la(ee,P[0].prev,P[V-1].next)}xp(Me,()=>{for(var cn=0;cn<V;cn++){var sn=P[cn];xn||(K.delete(sn.k),la(ee,sn.prev,sn.next)),Tr(sn.e,!xn)}})})(g,ce,Mn,ne)}}W&&Wl(()=>{if(y!==void 0)for(_ of y){var ee;(ee=_.a)===null||ee===void 0||ee.apply()}}),Xt.first=g.first&&g.first.e,Xt.last=ve&&ve.e}(h,l,i,s,n,r,o),a!==null&&(p===0?c?wl(c):c=na(()=>a(i)):c!==null&&Cs(c,()=>{c=null})),t(u))})}function gb(e,n,o,r){1&r&&ru(e.v,n),2&r?ru(e.i,o):e.i=o}function mb(e,n,o,r,s,a,i,l,c,d){var u=1&c?16&c?Da(s):E(s):s,h=2&c?Da(i):i,p={i:h,v:u,k:a,a:null,e:null,prev:o,next:r};try{return p.e=na(()=>l(e,u,h,d),!1),p.e.prev=o&&o.e,p.e.next=r&&r.e,o===null?n.first=p:(o.next=p,o.e.next=p.e),r!==null&&(r.prev=p,r.e.prev=p.e),p}finally{}}function rv(e,n,o){for(var r=e.next?e.next.e.nodes_start:o,s=n?n.e.nodes_start:o,a=e.e.nodes_start;a!==r;){var i=Hl(a);s.before(a),a=i}}function la(e,n,o){n===null?e.first=o:(n.next=o,n.e.next=o&&o.e),o!==null&&(o.prev=n,o.e.prev=n&&n.e)}function Op(e,n,o,r,s){var a,i=e,l="";Ya(()=>{var c;l!==(l=(c=n())!==null&&c!==void 0?c:"")&&(a!==void 0&&(Tr(a),a=void 0),l!==""&&(a=na(()=>{var d=l+"";o&&(d="<svg>".concat(d,"</svg>"));var u=Gu(d);if((o||r)&&(u=Qo(u)),Fa(Qo(u),u.lastChild),o||r)for(;Qo(u);)i.before(Qo(u));else i.before(u)})))})}function io(e,n,o,r,s){var a,i=(a=n.$$slots)===null||a===void 0?void 0:a[o],l=!1;i===!0&&(i=n[o==="default"?"children":o],l=!0),i===void 0?s!==null&&s(e):i(e,l?()=>r:r)}function Mp(e,n,o){var r,s,a=e;Ya(()=>{r!==(r=n())&&(s&&(Cs(s),s=null),r&&(s=na(()=>o(a,r))))},Ai)}function Uo(e,n,o){$o(()=>{var r=Rr(()=>n(e,o==null?void 0:o())||{});if(o&&r!=null&&r.update){var s=!1,a={};Ns(()=>{var i=o();O(i),s&&Hu(a,i)&&(a=i,r.update(i))}),s=!0}if(r!=null&&r.destroy)return()=>r.destroy()})}function $p(e){var n,o,r="";if(typeof e=="string"||typeof e=="number")r+=e;else if(typeof e=="object")if(Array.isArray(e)){var s=e.length;for(n=0;n<s;n++)e[n]&&(o=$p(e[n]))&&(r&&(r+=" "),r+=o)}else for(o in e)e[o]&&(r&&(r+=" "),r+=o);return r}function za(e){return typeof e=="object"?function(){for(var n,o,r=0,s="",a=arguments.length;r<a;r++)(n=arguments[r])&&(o=$p(n))&&(s&&(s+=" "),s+=o);return s}(e):e??""}var av=[...` 	
\r\f \v\uFEFF`];function sv(e){var n=arguments.length>1&&arguments[1]!==void 0&&arguments[1]?" !important;":";",o="";for(var r in e){var s=e[r];s!=null&&s!==""&&(o+=" "+r+": "+s+n)}return o}function Cc(e){return e[0]!=="-"||e[1]!=="-"?e.toLowerCase():e}function kn(e,n,o,r,s,a){var i=e.__className;if(i!==o||i===void 0){var l=function(u,h,p){var m=u==null?"":""+u;if(h&&(m=m?m+" "+h:h),p){for(var g in p)if(p[g])m=m?m+" "+g:g;else if(m.length)for(var b=g.length,j=0;(j=m.indexOf(g,j))>=0;){var x=j+b;j!==0&&!av.includes(m[j-1])||x!==m.length&&!av.includes(m[x])?j=x:m=(j===0?"":m.substring(0,j))+m.substring(x+1)}}return m===""?null:m}(o,r,a);l==null?e.removeAttribute("class"):n?e.className=l:e.setAttribute("class",l),e.__className=o}else if(a&&s!==a)for(var c in a){var d=!!a[c];s!=null&&d===!!s[c]||e.classList.toggle(c,d)}return a}function Sc(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{},o=arguments.length>2?arguments[2]:void 0,r=arguments.length>3?arguments[3]:void 0;for(var s in o){var a=o[s];n[s]!==a&&(o[s]==null?e.style.removeProperty(s):e.style.setProperty(s,a,r))}}function fr(e,n,o,r){if(e.__style!==n){var s=function(a,i){if(i){var l,c,d="";if(Array.isArray(i)?(l=i[0],c=i[1]):l=i,a){a=String(a).replaceAll(/\s*\/\*.*?\*\/\s*/g,"").trim();var u=!1,h=0,p=!1,m=[];l&&m.push(...Object.keys(l).map(Cc)),c&&m.push(...Object.keys(c).map(Cc));for(var g=0,b=-1,j=a.length,x=0;x<j;x++){var k=a[x];if(p?k==="/"&&a[x-1]==="*"&&(p=!1):u?u===k&&(u=!1):k==="/"&&a[x+1]==="*"?p=!0:k==='"'||k==="'"?u=k:k==="("?h++:k===")"&&h--,!p&&u===!1&&h===0){if(k===":"&&b===-1)b=x;else if(k===";"||x===j-1){if(b!==-1){var $=Cc(a.substring(g,b).trim());m.includes($)||(k!==";"&&x++,d+=" "+a.substring(g,x).trim()+";")}g=x+1,b=-1}}}}return l&&(d+=sv(l)),c&&(d+=sv(c,!0)),(d=d.trim())===""?null:d}return a==null?null:String(a)}(n,r);s==null?e.removeAttribute("style"):e.style.cssText=s,e.__style=n}else r&&(Array.isArray(r)?(Sc(e,o==null?void 0:o[0],r[0]),Sc(e,o==null?void 0:o[1],r[1],"important")):Sc(e,o,r));return r}var is=Symbol("class"),Zs=Symbol("style"),Ep=Symbol("is custom element"),Ap=Symbol("is html");function La(e,n){var o=Xu(e);o.value!==(o.value=n??void 0)&&(e.value!==n||n===0&&e.nodeName==="PROGRESS")&&(e.value=n??"")}function ht(e,n,o,r){var s=Xu(e);s[n]!==(s[n]=o)&&(n==="loading"&&(e[Xm]=o),o==null?e.removeAttribute(n):typeof o!="string"&&Rp(e).includes(n)?e[n]=o:e.setAttribute(n,o))}function al(e,n,o,r){var s,a=Xu(e),i=a[Ep],l=!a[Ap],c=n||{},d=e.tagName==="OPTION";for(var u in n)u in o||(o[u]=null);o.class?o.class=za(o.class):(r||o[is])&&(o.class=null),o[Zs]&&((s=o.style)!==null&&s!==void 0||(o.style=null));var h,p,m,g,b,j,x=Rp(e),k=function(S){var y=o[S];if(d&&S==="value"&&y==null)return e.value=e.__value="",c[S]=y,0;if(S==="class")return h=e.namespaceURI==="http://www.w3.org/1999/xhtml",kn(e,h,y,r,n==null?void 0:n[is],o[is]),c[S]=y,c[is]=o[is],0;if(S==="style")return fr(e,y,n==null?void 0:n[Zs],o[Zs]),c[S]=y,c[Zs]=o[Zs],0;if(y===(p=c[S])||(c[S]=y,(m=S[0]+S[1])==="$$"))return 0;if(m==="on"){var R={},N="$$"+S,_=S.slice(2);if(g=function(ne){return ub.includes(ne)}(_),function(ne){return ne.endsWith("capture")&&ne!=="gotpointercapture"&&ne!=="lostpointercapture"}(_)&&(_=_.slice(0,-7),R.capture=!0),!g&&p){if(y!=null)return 0;e.removeEventListener(_,c[N],R),c[N]=null}if(y!=null)if(g)e["__".concat(_)]=y,Ii([_]);else{let ne=function(ye){c[S].call(this,ye)};c[N]=Sp(_,e,ne,R)}else g&&(e["__".concat(_)]=void 0)}else if(S==="style")ht(e,S,y);else if(S==="autofocus")(function(ne,ye){if(ye){var te=document.body;ne.autofocus=!0,Wl(()=>{document.activeElement===te&&ne.focus()})}})(e,!!y);else if(i||S!=="__value"&&(S!=="value"||y==null))if(S==="selected"&&d)(function(ne,ye){ye?ne.hasAttribute("selected")||ne.setAttribute("selected",""):ne.removeAttribute("selected")})(e,y);else if(b=S,l||(b=function(ne){var ye;return ne=ne.toLowerCase(),(ye=db[ne])!==null&&ye!==void 0?ye:ne}(b)),j=b==="defaultValue"||b==="defaultChecked",y!=null||i||j)j||x.includes(b)&&(i||typeof y!="string")?e[b]=y:typeof y!="function"&&ht(e,b,y);else if(a[S]=null,b==="value"||b==="checked"){var z=e,W=n===void 0;if(b==="value"){var X=z.defaultValue;z.removeAttribute(b),z.defaultValue=X,z.value=z.__value=W?X:null}else{var H=z.defaultChecked;z.removeAttribute(b),z.defaultChecked=H,z.checked=!!W&&H}}else e.removeAttribute(S);else e.value=e.__value=y};for(var $ in o)k($);return c}function Xu(e){var n;return(n=e.__attributes)!==null&&n!==void 0?n:e.__attributes={[Ep]:e.nodeName.includes("-"),[Ap]:e.namespaceURI==="http://www.w3.org/1999/xhtml"}}var iv=new Map;function Rp(e){var n,o=iv.get(e.nodeName);if(o)return o;iv.set(e.nodeName,o=[]);for(var r=e,s=Element.prototype;s!==r;){for(var a in n=ep(r))n[a].set&&o.push(a);r=Vu(r)}return o}function kl(e,n){var o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:n,r=Us();pb(e,"input",s=>{var a=s?e.defaultValue:e.value;if(a=zc(e)?Oc(a):a,o(a),r&&a!==(a=n())){var i=e.selectionStart,l=e.selectionEnd;e.value=a??"",l!==null&&(e.selectionStart=i,e.selectionEnd=Math.min(l,e.value.length))}}),Rr(n)==null&&e.value&&o(zc(e)?Oc(e.value):e.value),Ns(()=>{var s=n();zc(e)&&s===Oc(e.value)||(e.type!=="date"||s||e.value)&&s!==e.value&&(e.value=s??"")})}function zc(e){var n=e.type;return n==="number"||n==="range"}function Oc(e){return e===""?null:+e}function hn(e,n,o){var r=Kr(e,n);r&&r.set&&(e[n]=o,Ku(()=>{e[n]=null}))}function lv(e,n){return e===n||(e==null?void 0:e[xa])===n}function Yt(){var e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{},n=arguments.length>1?arguments[1]:void 0,o=arguments.length>2?arguments[2]:void 0;return $o(()=>{var r,s;return Ns(()=>{r=s,s=[],Rr(()=>{e!==o(...s)&&(n(e,...s),r&&lv(o(...r),e)&&n(null,...r))})}),()=>{Wl(()=>{s&&lv(o(...s),e)&&n(null,...s)})}}),e}function Jr(e){return function(){for(var n=arguments.length,o=new Array(n),r=0;r<n;r++)o[r]=arguments[r];return o[0].stopPropagation(),e==null?void 0:e.apply(this,o)}}function ua(e){return function(){for(var n=arguments.length,o=new Array(n),r=0;r<n;r++)o[r]=arguments[r];return o[0].preventDefault(),e==null?void 0:e.apply(this,o)}}function jn(){var e=arguments.length>0&&arguments[0]!==void 0&&arguments[0],n=kt,o=n.l.u;if(o){var r,s=()=>O(n.s);if(e){var a=0,i={},l=ks(()=>{var c=!1,d=n.s;for(var u in d)d[u]!==i[u]&&(i[u]=d[u],c=!0);return c&&a++,a});s=()=>t(l)}o.b.length&&(r=()=>{cv(n,s),ml(o.b)},gp(),Ns(r)),su(()=>{var c=Rr(()=>o.m.map(ob));return()=>{for(var d of c)typeof d=="function"&&d()}}),o.a.length&&su(()=>{cv(n,s),ml(o.a)})}}function cv(e,n){if(e.l.s)for(var o of e.l.s)t(o);n()}function Gl(e){var n=Da(0);return function(){return arguments.length===1?(v(n,t(n)+1),arguments[0]):(t(n),e())}}function ai(e,n){var o,r=(o=e.$$events)===null||o===void 0?void 0:o[n.type],s=Ri(r)?r.slice():r==null?[]:[r];for(var a of s)a.call(this,n)}function Po(e){kt===null&&Pi(),Rs&&kt.l!==null?Tp(kt).m.push(e):su(()=>{var n=Rr(e);if(typeof n=="function")return n})}function rr(e){kt===null&&Pi(),Po(()=>()=>Rr(e))}function bb(){var e=kt;return e===null&&Pi(),(n,o,r)=>{var s,a=(s=e.s.$$events)===null||s===void 0?void 0:s[n];if(a){var i=Ri(a)?a.slice():[a],l=function(d,u){var{bubbles:h=!1,cancelable:p=!1}=arguments.length>2&&arguments[2]!==void 0?arguments[2]:{};return new CustomEvent(d,{detail:u,bubbles:h,cancelable:p})}(n,o,r);for(var c of i)c.call(e.x,l);return!l.defaultPrevented}return!0}}function jb(e){kt===null&&Pi(),kt.l===null&&function(){throw new Error("https://svelte.dev/e/lifecycle_legacy_only")}(),Tp(kt).b.push(e)}function Tp(e){var n,o=e.l;return(n=o.u)!==null&&n!==void 0?n:o.u={a:[],b:[],m:[]}}var Vi=!1,xb={get(e,n){if(!e.exclude.includes(n))return t(e.version),n in e.special?e.special[n]():e.props[n]},set:(e,n,o)=>(n in e.special||(e.special[n]=f({get[n](){return e.props[n]}},n,4)),e.special[n](o),ev(e.version),!0),getOwnPropertyDescriptor(e,n){if(!e.exclude.includes(n))return n in e.props?{enumerable:!0,configurable:!0,value:e.props[n]}:void 0},deleteProperty:(e,n)=>(e.exclude.includes(n)||(e.exclude.push(n),ev(e.version)),!0),has:(e,n)=>!e.exclude.includes(n)&&n in e.props,ownKeys:e=>Reflect.ownKeys(e.props).filter(n=>!e.exclude.includes(n))};function Hi(e,n){return new Proxy({props:e,exclude:n,special:{},version:Da(0)},xb)}var yb={get(e,n){for(var o=e.props.length;o--;){var r=e.props[o];if(Xs(r)&&(r=r()),typeof r=="object"&&r!==null&&n in r)return r[n]}},set(e,n,o){for(var r=e.props.length;r--;){var s=e.props[r];Xs(s)&&(s=s());var a=Kr(s,n);if(a&&a.set)return a.set(o),!0}return!1},getOwnPropertyDescriptor(e,n){for(var o=e.props.length;o--;){var r=e.props[o];if(Xs(r)&&(r=r()),typeof r=="object"&&r!==null&&n in r){var s=Kr(r,n);return s&&!s.configurable&&(s.configurable=!0),s}}},has(e,n){if(n===xa||n===Zf)return!1;for(var o of e.props)if(Xs(o)&&(o=o()),o!=null&&n in o)return!0;return!1},ownKeys(e){var n=[];for(var o of e.props)for(var r in Xs(o)&&(o=o()),o)n.includes(r)||n.push(r);return n}};function ya(){for(var e=arguments.length,n=new Array(e),o=0;o<e;o++)n[o]=arguments[o];return new Proxy({props:n},yb)}function uv(e){var n,o;return(n=(o=e.ctx)===null||o===void 0?void 0:o.d)!==null&&n!==void 0&&n}function f(e,n,o,r){var s,a,i,l=!!(1&o),c=!Rs||!!(2&o),d=!!(8&o),u=!!(16&o),h=!1;d?[i,h]=function(_){var z=Vi;try{return Vi=!1,[_(),Vi]}finally{Vi=z}}(()=>e[n]):i=e[n];var p,m=xa in e||Zf in e,g=d&&((s=(a=Kr(e,n))===null||a===void 0?void 0:a.set)!==null&&s!==void 0?s:m&&n in e&&(_=>e[n]=_))||void 0,b=r,j=!0,x=!1,k=()=>(x=!0,j&&(j=!1,b=u?Rr(r):r),b);if(i===void 0&&r!==void 0&&(g&&c&&function(){throw new Error("https://svelte.dev/e/props_invalid_value")}(),i=k(),g&&g(i)),c)p=()=>{var _=e[n];return _===void 0?k():(j=!0,x=!1,_)};else{var $=(l?ks:ae)(()=>e[n]);$.f|=Qm,p=()=>{var _=t($);return _!==void 0&&(b=void 0),_===void 0?b:_}}if(!(4&o))return p;if(g){var S=e.$$legacy;return function(_,z){return arguments.length>0?(c&&z&&!S&&!h||g(z?p():_),_):p()}}var y=!1,R=E(i),N=ks(()=>{var _=p(),z=t(R);return y?(y=!1,z):R.v=_});return d&&t(N),l||(N.equals=Yu),function(_,z){if(arguments.length>0){var W=z?t(N):c&&d?Ra(_):_;if(!N.equals(W)){if(y=!0,v(R,W),x&&b!==void 0&&(b=W),uv(N))return _;Rr(()=>t(N))}return _}return uv(N)?N.v:t(N)}}function So(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:function(r){var s=function(a){try{if(typeof window<"u"&&window.localStorage!==void 0)return window.localStorage[a]}catch{}}("debug");return s!=null&&s.endsWith("*")?r.startsWith(s.slice(0,-1)):r===s}(e);if(!n)return wb;var o=function(r){for(var s=0,a=0;a<r.length;a++)s=(s<<5)-s+r.charCodeAt(a),s|=0;return dv[Math.abs(s)%dv.length]}(e);return function(){for(var r=arguments.length,s=new Array(r),a=0;a<r;a++)s[a]=arguments[a];console.log("%c".concat(e),"color:".concat(o),...s)}}function wb(){}var dv=["#0000CC","#0099FF","#009400","#8dd200","#CCCC00","#CC9933","#ae04e7","#ff35d7","#FF3333","#FF6600","#FF9933","#FFCC33"],kb=0;function us(){return++kb}function Eo(e){return parseInt(e,10)}function Zu(e){return Cb.test(e)}var Cb=/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/;function gt(e){return typeof e=="object"&&e!==null&&(e.constructor===void 0||e.constructor.name==="Object")}function po(e){return typeof e=="object"&&e!==null&&(e.constructor===void 0||e.constructor.name==="Object"||e.constructor.name==="Array")}function Sb(e){return e===!0||e===!1}function cu(e){if(typeof e=="number")return e>9466848e5&&isFinite(e)&&Math.floor(e)===e&&!isNaN(new Date(e).valueOf());if(typeof e=="bigint")return cu(Number(e));try{var n=e&&e.valueOf();if(n!==e)return cu(n)}catch{return!1}return!1}function Pp(e){(Yi=Yi||window.document.createElement("div")).style.color="",Yi.style.color=e;var n=Yi.style.color;return n!==""?n.replace(/\s+/g,"").toLowerCase():void 0}var Yi=void 0;function zb(e){return typeof e=="string"&&e.length<99&&!!Pp(e)}function ed(e,n){if(typeof e=="number"||typeof e=="string"||typeof e=="boolean"||e===void 0)return typeof e;if(typeof e=="bigint")return"number";if(e===null)return"null";if(Array.isArray(e))return"array";if(gt(e))return"object";var o=n.stringify(e);return o&&Zu(o)?"number":o==="true"||o==="false"?"boolean":o==="null"?"null":"unknown"}var Ob=/^https?:\/\/\S+$/;function Xl(e){return typeof e=="string"&&Ob.test(e)}function _s(e,n){if(e==="")return"";var o=e.trim();return o==="null"?null:o==="true"||o!=="false"&&(Zu(o)?n.parse(o):e)}var Mb=[];function vv(e,n){if(e.length!==n.length)return!1;for(var o=0;o<e.length;o++)if(e[o]!==n[o])return!1;return!0}function uu(e){var n=arguments.length>1&&arguments[1]!==void 0&&arguments[1],o={};if(!Array.isArray(e))throw new TypeError("Array expected");function r(i,l){(!Array.isArray(i)&&!gt(i)||n&&l.length>0)&&(o[Sn(l)]=!0),gt(i)&&Object.keys(i).forEach(c=>{r(i[c],l.concat(c))})}for(var s=Math.min(e.length,1e4),a=0;a<s;a++)r(e[a],Mb);return Object.keys(o).sort().map(Sr)}function Ip(e,n,o){if(!(n<=e))for(var r=e;r<n;r++)o(r)}function qp(e,n){return e.length>n?e.slice(0,n):e}function fv(e){return me({},e)}function pv(e){return Object.values(e)}function hv(e,n,o,r){var s=e.slice(0),a=s.splice(n,o);return s.splice.apply(s,[n+r,0,...a]),s}function $b(e,n,o){return e.slice(0,n).concat(o).concat(e.slice(n))}function qi(e,n){try{return n.parse(e)}catch{return n.parse(Or(e))}}function Np(e,n){try{return qi(e,n)}catch{return}}function Zl(e,n){e=e.replace(_p,"");try{return n(e)}catch{}try{return n("{"+e+"}")}catch{}try{return n("["+e+"]")}catch{}throw new Error("Failed to parse partial JSON")}function Up(e){e=e.replace(_p,"");try{return Or(e)}catch{}try{var n=Or("["+e+"]");return n.substring(1,n.length-1)}catch{}try{var o=Or("{"+e+"}");return o.substring(1,o.length-1)}catch{}throw new Error("Failed to repair partial JSON")}var _p=/,\s*$/;function Ss(e,n){var o=mv.exec(n);if(o){var r=Eo(o[2]),s=function(m,g){for(var b=arguments.length>2&&arguments[2]!==void 0?arguments[2]:0,j=arguments.length>3&&arguments[3]!==void 0?arguments[3]:m.length,x=0,k=b;k<j;k++)m.charAt(k)===g&&x++;return x}(e,`
`,0,r),a=r-e.lastIndexOf(`
`,r)-1;return{position:r,line:s,column:a,message:n.replace(mv,()=>"line ".concat(s+1," column ").concat(a+1))}}var i=Tb.exec(n),l=i?Eo(i[1]):void 0,c=l!==void 0?l-1:void 0,d=Pb.exec(n),u=d?Eo(d[1]):void 0,h=u!==void 0?u-1:void 0,p=c!==void 0&&h!==void 0?function(m,g,b){for(var j=m.indexOf(`
`),x=1;x<g&&j!==-1;)j=m.indexOf(`
`,j+1),x++;return j!==-1?j+b+1:void 0}(e,c,h):void 0;return{position:p,line:c,column:h,message:n.replace(/^JSON.parse: /,"").replace(/ of the JSON data$/,"")}}function Mc(e){return gt(e)?e.json!==void 0?e.text!==void 0?'Content must contain either a property "json" or a property "text" but not both':void 0:e.text===void 0?'Content must contain either a property "json" or a property "text"':typeof e.text!="string"?'Content "text" property must be a string containing a JSON document. Did you mean to use the "json" property instead?':void 0:"Content must be an object"}function gi(e){return gt(e)&&typeof e.text=="string"}function mi(e){return gt(e)&&e.json!==void 0}function Eb(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:void 0,o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:JSON;return gi(e)?e:{text:o.stringify(e.json,null,n)}}function gv(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:JSON;return mi(e)?e:{json:n.parse(e.text)}}function du(e,n,o){return Eb(e,n,o).text}function Ab(e,n){return Rb(e,n)>n}function Rb(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1/0;if(gi(e))return e.text.length;var o=e.json,r=0;return function s(a){if(Array.isArray(a)){if((r+=a.length-1+2)>n)return;for(var i=0;i<a.length;i++)if(s(a[i]),r>n)return}else if(gt(a)){var l=Object.keys(a);r+=2+l.length+(l.length-1);for(var c=0;c<l.length;c++){var d=l[c],u=a[d];r+=d.length+2,s(u)}}else r+=typeof a=="string"?a.length+2:String(a).length}(o),r}var mv=/(position|char) (\d+)/,Tb=/line (\d+)/,Pb=/column (\d+)/;function Ib(e,n){return e.parse===n.parse&&e.stringify===n.stringify}var yo,$t,dr,pr,hr,lr,wa,qb=/[,:]\S/;function nd(e){var{escapeControlCharacters:n,escapeUnicodeCharacters:o}=e;return n?o?Nb:Ub:o?_b:Db}(function(e){e.text="text",e.tree="tree",e.table="table"})(yo||(yo={})),function(e){e.after="after",e.inside="inside",e.key="key",e.value="value",e.multi="multi",e.text="text"}($t||($t={})),function(e){e.after="after",e.key="key",e.value="value",e.inside="inside"}(dr||(dr={})),function(e){e.info="info",e.warning="warning",e.error="error"}(pr||(pr={})),function(e){e.key="key",e.value="value"}(hr||(hr={})),function(e){e.asc="asc",e.desc="desc"}(lr||(lr={})),function(e){e.no="no",e.self="self",e.nextInside="nextInside"}(wa||(wa={}));var Nb={escapeValue:e=>Dp(Jp(String(e))),unescapeValue:e=>Bp(Fp(e))},Ub={escapeValue:e=>Jp(String(e)),unescapeValue:e=>Bp(e)},_b={escapeValue:e=>Dp(String(e)),unescapeValue:e=>Fp(e)},Db={escapeValue:e=>String(e),unescapeValue:e=>e};function Dp(e){return e.replace(/[^\x20-\x7F]/g,n=>{var o;return n==="\b"||n==="\f"||n===`
`||n==="\r"||n==="	"?n:"\\u"+("000"+((o=n.codePointAt(0))===null||o===void 0?void 0:o.toString(16))).slice(-4)})}function Fp(e){return e.replace(/\\u[a-fA-F0-9]{4}/g,n=>{try{var o=JSON.parse('"'+n+'"');return Lp[o]||o}catch{return n}})}var Lp={'"':'\\"',"\\":"\\\\","\b":"\\b","\f":"\\f","\n":"\\n","\r":"\\r","	":"\\t"},Fb={'\\"':'"',"\\\\":"\\","\\/":"/","\\b":"\b","\\f":"\f","\\n":`
`,"\\r":"\r","\\t":"	"};function Jp(e){return e.replace(/["\b\f\n\r\t\\]/g,n=>Lp[n]||n)}function Bp(e){return e.replace(/\\["bfnrt\\]/g,n=>Fb[n]||n)}function ec(e){return typeof e!="string"?String(e):e.endsWith(`
`)?e+`
`:e}function Wp(e,n){return Ds(e,o=>o.nodeName.toUpperCase()===n.toUpperCase())}function ma(e,n,o){return Ds(e,r=>function(s,a,i){return typeof s.getAttribute=="function"&&s.getAttribute(a)===i}(r,n,o))}function Ds(e,n){return!!td(e,n)}function td(e,n){for(var o=e;o&&!n(o);)o=o.parentNode;return o}function Ni(e){var n,o;return(n=e==null||(o=e.ownerDocument)===null||o===void 0?void 0:o.defaultView)!==null&&n!==void 0?n:void 0}function od(e){var n=Ni(e),o=n==null?void 0:n.document.activeElement;return!!o&&Ds(o,r=>r===e)}function Vp(e,n){return td(e,o=>o.nodeName===n)}function $c(e){return ma(e,"data-type","selectable-key")?$t.key:ma(e,"data-type","selectable-value")?$t.value:ma(e,"data-type","insert-selection-area-inside")?$t.inside:ma(e,"data-type","insert-selection-area-after")?$t.after:$t.multi}function vu(e){return encodeURIComponent(Sn(e))}function Hp(e){var n,o=td(e,s=>!(s==null||!s.hasAttribute)&&s.hasAttribute("data-path")),r=(n=o==null?void 0:o.getAttribute("data-path"))!==null&&n!==void 0?n:void 0;return r?Sr(decodeURIComponent(r)):void 0}function Lb(e){var{allElements:n,currentElement:o,direction:r,hasPrio:s=()=>!0,margin:a=10}=e,i=Sm(n.filter(function(x){var k=x.getBoundingClientRect();return k.width>0&&k.height>0}),c),l=c(o);function c(x){var k=x.getBoundingClientRect();return{x:k.left+k.width/2,y:k.top+k.height/2,rect:k,element:x}}function d(x,k){var $=arguments.length>2&&arguments[2]!==void 0?arguments[2]:1,S=x.x-k.x,y=(x.y-k.y)*$;return Math.sqrt(S*S+y*y)}var u=x=>d(x,l);if(r==="Left"||r==="Right"){var h=r==="Left"?i.filter(x=>{return k=l,x.rect.left+a<k.rect.left;var k}):i.filter(x=>{return k=l,x.rect.right>k.rect.right+a;var k}),p=h.filter(x=>{return k=x,$=l,Math.abs(k.y-$.y)<a;var k,$}),m=Bi(p,u)||Bi(h,x=>d(x,l,10));return m==null?void 0:m.element}if(r==="Up"||r==="Down"){var g=r==="Up"?i.filter(x=>{return k=l,x.y+a<k.y;var k}):i.filter(x=>{return k=l,x.y>k.y+a;var k}),b=g.filter(x=>s(x.element)),j=Bi(b,u)||Bi(g,u);return j==null?void 0:j.element}}function rd(){var e,n,o,r;return typeof navigator<"u"&&(e=(n=(o=navigator)===null||o===void 0||(o=o.platform)===null||o===void 0?void 0:o.toUpperCase().includes("MAC"))!==null&&n!==void 0?n:(r=navigator)===null||r===void 0||(r=r.userAgentData)===null||r===void 0||(r=r.platform)===null||r===void 0?void 0:r.toUpperCase().includes("MAC"))!==null&&e!==void 0&&e}function ta(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:"+",o=[];ad(e,arguments.length>2&&arguments[2]!==void 0?arguments[2]:rd)&&o.push("Ctrl"),e.altKey&&o.push("Alt"),e.shiftKey&&o.push("Shift");var r=e.key.length===1?e.key.toUpperCase():e.key;return r in Jb||o.push(r),o.join(n)}function ad(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:rd;return e.ctrlKey||e.metaKey&&n()}var Jb={Ctrl:!0,Command:!0,Control:!0,Alt:!0,Option:!0,Shift:!0};function fn(e,n){n===void 0&&(n={});var o=n.insertAt;if(e&&typeof document<"u"){var r=document.head||document.getElementsByTagName("head")[0],s=document.createElement("style");s.type="text/css",o==="top"&&r.firstChild?r.insertBefore(s,r.firstChild):r.appendChild(s),s.styleSheet?s.styleSheet.cssText=e:s.appendChild(document.createTextNode(e))}}fn(`.jse-absolute-popup.svelte-1r8q3m8 {
  position: relative;
  left: 0;
  top: 0;
  width: 0;
  height: 0;
  z-index: 1001;
}
.jse-absolute-popup.svelte-1r8q3m8 .jse-hidden-input:where(.svelte-1r8q3m8) {
  position: fixed;
  left: 0;
  top: 0;
  width: 0;
  height: 0;
  padding: 0;
  margin: 0;
  border: none;
  outline: none;
  overflow: hidden;
}
.jse-absolute-popup.svelte-1r8q3m8 .jse-absolute-popup-content:where(.svelte-1r8q3m8) {
  position: absolute;
}`);var Bb=L('<div class="jse-absolute-popup-content svelte-1r8q3m8"><input type="text" readonly="" tabindex="-1" class="jse-hidden-input svelte-1r8q3m8"> <!></div>'),Wb=L('<div role="none" class="jse-absolute-popup svelte-1r8q3m8"><!></div>');function Vb(e,n){en(n,!1);var o=f(n,"popup",8),r=f(n,"closeAbsolutePopup",8),s=E(),a=E();function i(h){o().options&&o().options.closeOnOuterClick&&!Ds(h.target,p=>p===t(s))&&r()(o().id)}function l(h){ta(h)==="Escape"&&(h.preventDefault(),h.stopPropagation(),r()(o().id))}Po(function(){t(a)&&t(a).focus()}),jn();var c=Wb();ie("mousedown",Qr,function(h){i(h)},!0),ie("keydown",Qr,l,!0),ie("wheel",Qr,function(h){i(h)},!0);var d=M(c),u=h=>{var p=Bb(),m=M(p);Yt(m,g=>v(a,g),()=>t(a)),Mp(q(m,2),()=>o().component,(g,b)=>{b(g,ya(()=>o().props))}),be(g=>fr(p,g),[()=>function(g,b){var j=g.getBoundingClientRect(),{left:x,top:k,positionAbove:$,positionLeft:S}=function(){if(b.anchor){var{anchor:y,width:R=0,height:N=0,offsetTop:_=0,offsetLeft:z=0,position:W}=b,{left:X,top:H,bottom:ne,right:ye}=y.getBoundingClientRect(),te=W==="top"||H+N>window.innerHeight&&H>N,ve=W==="left"||X+R>window.innerWidth&&X>R;return{left:ve?ye-z:X+z,top:te?H-_:ne+_,positionAbove:te,positionLeft:ve}}if(typeof b.left=="number"&&typeof b.top=="number"){var{left:Re,top:Ee,width:fe=0,height:Se=0}=b;return{left:Re,top:Ee,positionAbove:Ee+Se>window.innerHeight&&Ee>Se,positionLeft:Re+fe>window.innerWidth&&Re>fe}}throw new Error('Invalid config: pass either "left" and "top", or pass "anchor"')}();return($?"bottom: ".concat(j.top-k,"px;"):"top: ".concat(k-j.top,"px;"))+(S?"right: ".concat(j.left-x,"px;"):"left: ".concat(x-j.left,"px;"))}(t(s),o().options)],ae),A(h,p)};Q(d,h=>{t(s)&&h(u)}),Yt(c,h=>v(s,h),()=>t(s)),ie("mousedown",c,function(h){h.stopPropagation()}),ie("keydown",c,l),A(e,c),nn()}var Hb=L("<!> <!>",1);function fu(e,n){en(n,!1);var o,r,s=So("jsoneditor:AbsolutePopup"),a=E([],!0);function i(d){var u=t(a).findIndex(p=>p.id===d);if(u!==-1){var h=t(a)[u];h.options.onClose&&h.options.onClose(),v(a,t(a).filter(p=>p.id!==d))}}o="absolute-popup",r={openAbsolutePopup:function(d,u,h){s("open...",u,h);var p={id:us(),component:d,props:u||{},options:h||{}};return v(a,[...t(a),p]),p.id},closeAbsolutePopup:i},wp().set(o,r),F(()=>t(a),()=>{s("popups",t(a))}),mt(),jn(!0);var l=Hb(),c=rn(l);lo(c,1,()=>t(a),ho,(d,u)=>{Vb(d,{get popup(){return t(u)},closeAbsolutePopup:i})}),io(q(c,2),n,"default",{},null),A(e,l),nn()}function Ui(e,n){for(var o=new Set(n),r=e.replace(/ \(copy( \d+)?\)$/,""),s=e,a=1;o.has(s);){var i="copy"+(a>1?" "+a:"");s="".concat(r," (").concat(i,")"),a++}return s}function bi(e,n){var o=n-3;return e.length>n?e.substring(0,o)+"...":e}function Yb(e){if(e==="")return"";var n=e.toLowerCase();if(n==="null")return null;if(n==="true")return!0;if(n==="false")return!1;if(n!=="undefined"){var o=Number(e),r=parseFloat(e);return isNaN(o)||isNaN(r)?e:o}}var Kb={id:"jsonquery",name:"JSONQuery",description:`
<p>
  Enter a <a href="https://jsonquerylang.org" target="_blank" 
  rel="noopener noreferrer">JSON Query</a> function to filter, sort, or transform the data.
  You can use functions like <code>get</code>, <code>filter</code>,
  <code>sort</code>, <code>pick</code>, <code>groupBy</code>, <code>uniq</code>, etcetera. 
  Example query: <code>filter(.age >= 18)</code>
</p>
`,createQuery:function(e,n){var{filter:o,sort:r,projection:s}=n,a=[];o&&o.path&&o.relation&&o.value&&a.push(["filter",[(i=o.relation,Og("1 ".concat(i," 1"))[0]),Ki(o.path),Yb(o.value)]]);var i;return r&&r.path&&r.direction&&a.push(["sort",Ki(r.path),r.direction==="desc"?"desc":"asc"]),s&&s.paths&&(s.paths.length>1?a.push(["pick",...s.paths.map(Ki)]):a.push(["map",Ki(s.paths[0])])),Mg(["pipe",...a])},executeQuery:function(e,n){return n.trim()!==""?$g(e,n):e}};function Ki(e){return["get",...e]}var Qb=$a("<g><!></g>");function Gb(e,n){en(n,!1);var o=870711,r=E(""),s=f(n,"data",8);function a(l){if(!l||!l.raw)return"";var c=l.raw,d={};return c=c.replace(/\s(?:xml:)?id=["']?([^"')\s]+)/g,(u,h)=>{var p="fa-".concat((o+=1).toString(16));return d[h]=p,' id="'.concat(p,'"')}),c=c.replace(/#(?:([^'")\s]+)|xpointer\(id\((['"]?)([^')]+)\2\)\))/g,(u,h,p,m)=>{var g=h||m;return g&&d[g]?"#".concat(d[g]):u}),c}F(()=>O(s()),()=>{v(r,a(s()))}),mt();var i=Qb();Op(M(i),()=>t(r),!0,!1),A(e,i),nn()}fn(`
  .fa-icon.svelte-1mc5hvj {
    display: inline-block;
    fill: currentColor;
  }
  .fa-flip-horizontal.svelte-1mc5hvj {
    transform: scale(-1, 1);
  }
  .fa-flip-vertical.svelte-1mc5hvj {
    transform: scale(1, -1);
  }
  .fa-spin.svelte-1mc5hvj {
    animation: svelte-1mc5hvj-fa-spin 1s 0s infinite linear;
  }
  .fa-inverse.svelte-1mc5hvj {
    color: #fff;
  }
  .fa-pulse.svelte-1mc5hvj {
    animation: svelte-1mc5hvj-fa-spin 1s infinite steps(8);
  }
  @keyframes svelte-1mc5hvj-fa-spin {
    0% {
      transform: rotate(0deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }
`);var Xb=$a("<svg><!></svg>"),Zb=$a("<path></path>"),e0=$a("<polygon></polygon>"),n0=$a("<!><!><!>",1);function nt(e,n){var o=Hi(n,["children","$$slots","$$events","$$legacy"]),r=Hi(o,["class","data","scale","spin","inverse","pulse","flip","label","style"]);en(n,!1);var s=f(n,"class",8,""),a=f(n,"data",8),i=E(),l=f(n,"scale",8,1),c=f(n,"spin",8,!1),d=f(n,"inverse",8,!1),u=f(n,"pulse",8,!1),h=f(n,"flip",8,void 0),p=f(n,"label",8,""),m=f(n,"style",8,""),g=E(10),b=E(10),j=E(),x=E();function k(){var S=1;return l()!==void 0&&(S=Number(l())),isNaN(S)||S<=0?(console.warn('Invalid prop: prop "scale" should be a number over 0.'),1):1*S}function $(){return t(i)?Math.max(t(i).width,t(i).height)/16:1}F(()=>(O(a()),O(m()),O(l())),()=>{v(i,function(S){var y;if(S){if(!("definition"in S)){if("iconName"in S&&"icon"in S){S.iconName;var[R,N,,,_]=S.icon;y={width:R,height:N,paths:(Array.isArray(_)?_:[_]).map(z=>({d:z}))}}else y=S[Object.keys(S)[0]];return y}console.error("`import faIconName from '@fortawesome/package-name/faIconName` not supported - Please use `import { faIconName } from '@fortawesome/package-name/faIconName'` instead")}}(a())),m(),l(),v(g,t(i)?t(i).width/$()*k():0),v(b,t(i)?t(i).height/$()*k():0),v(j,function(){var S="";m()!==null&&(S+=m());var y=k();return y===1?S.length===0?"":S:(S===""||S.endsWith(";")||(S+="; "),"".concat(S,"font-size: ").concat(y,"em"))}()),v(x,t(i)?"0 0 ".concat(t(i).width," ").concat(t(i).height):"0 0 ".concat(t(g)," ").concat(t(b)))}),mt(),jn(),function(S,y){var R,N=Hi(y,["children","$$slots","$$events","$$legacy"]),_=Hi(N,["class","width","height","box","spin","inverse","pulse","flip","style","label"]),z=f(y,"class",8,""),W=f(y,"width",8),X=f(y,"height",8),H=f(y,"box",8,"0 0 0 0"),ne=f(y,"spin",8,!1),ye=f(y,"inverse",8,!1),te=f(y,"pulse",8,!1),ve=f(y,"flip",8,"none"),Re=f(y,"style",8,""),Ee=f(y,"label",8,""),fe=Xb();io(M(fe),y,"default",{},null),be(Se=>{var we;return R=al(fe,R,me(me({version:"1.1",class:"fa-icon ".concat((we=z())!==null&&we!==void 0?we:""),width:W(),height:X(),"aria-label":Ee(),role:Ee()?"img":"presentation",viewBox:H(),style:Re()},_),{},{[is]:Se}),"svelte-1mc5hvj")},[()=>({"fa-spin":ne(),"fa-pulse":te(),"fa-inverse":ye(),"fa-flip-horizontal":ve()==="horizontal","fa-flip-vertical":ve()==="vertical"})],ae),A(S,fe)}(e,ya({get label(){return p()},get width(){return t(g)},get height(){return t(b)},get box(){return t(x)},get style(){return t(j)},get spin(){return c()},get flip(){return h()},get inverse(){return d()},get pulse(){return u()},get class(){return s()}},()=>r,{children:(S,y)=>{var R=to();io(rn(R),n,"default",{},N=>{var _=n0(),z=rn(_);lo(z,1,()=>{var ne;return((ne=t(i))===null||ne===void 0?void 0:ne.paths)||[]},ho,(ne,ye)=>{var te,ve=Zb();be(()=>te=al(ve,te,me({},t(ye)))),A(ne,ve)});var W=q(z);lo(W,1,()=>{var ne;return((ne=t(i))===null||ne===void 0?void 0:ne.polygons)||[]},ho,(ne,ye)=>{var te,ve=e0();be(()=>te=al(ve,te,me({},t(ye)))),A(ne,ve)});var X=q(W),H=ne=>{Gb(ne,{get data(){return t(i)},set data(ye){v(i,ye)},$$legacy:!0})};Q(X,ne=>{var ye;(ye=t(i))!==null&&ye!==void 0&&ye.raw&&ne(H)}),A(N,_)}),A(S,R)},$$slots:{default:!0}})),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-boolean-toggle.svelte-1ryp01u {
  padding: 0;
  margin: 1px 0 0;
  vertical-align: top;
  display: inline-flex;
  color: var(--jse-value-color-boolean, #ff8c00);
}

.jse-boolean-toggle.svelte-1ryp01u:not(.jse-readonly) {
  cursor: pointer;
}`);var t0=L('<div role="checkbox" tabindex="-1"><!></div>');function o0(e,n){en(n,!1);var o=f(n,"path",9),r=f(n,"value",9),s=f(n,"readOnly",9),a=f(n,"onPatch",9),i=f(n,"focus",9);jn(!0);var l,c=t0(),d=M(c),u=ae(()=>r()===!0?_f:Df);nt(d,{get data(){return t(u)}}),be(h=>{ht(c,"aria-checked",r()===!0),l=kn(c,1,"jse-boolean-toggle svelte-1ryp01u",null,l,h),ht(c,"title",s()?"Boolean value ".concat(r()):"Click to toggle this boolean value")},[()=>({"jse-readonly":s()})],ae),ie("mousedown",c,function(h){h.stopPropagation(),s()||(a()([{op:"replace",path:Sn(o()),value:!r()}]),i()())}),A(e,c),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-color-picker-popup.svelte-s1wu8v .picker_wrapper.popup,
.jse-color-picker-popup.svelte-s1wu8v .picker_wrapper.popup .picker_arrow::before,
.jse-color-picker-popup.svelte-s1wu8v .picker_wrapper.popup .picker_arrow::after {
  background: var(--jse-color-picker-background, var(--jse-panel-background, #ebebeb));
  line-height: normal;
}
.jse-color-picker-popup.svelte-s1wu8v .picker_slider,
.jse-color-picker-popup.svelte-s1wu8v .picker_sl,
.jse-color-picker-popup.svelte-s1wu8v .picker_editor input,
.jse-color-picker-popup.svelte-s1wu8v .picker_sample,
.jse-color-picker-popup.svelte-s1wu8v .picker_done button {
  box-shadow: var(--jse-color-picker-border-box-shadow, #cbcbcb 0 0 0 1px);
}
.jse-color-picker-popup.svelte-s1wu8v .picker_editor input {
  background: var(--jse-background-color, #fff);
  color: var(--jse-text-color, #4d4d4d);
}
.jse-color-picker-popup.svelte-s1wu8v .picker_done button {
  background: var(--jse-button-background, #e0e0e0);
  color: var(--jse-button-color, var(--jse-text-color, #4d4d4d));
}
.jse-color-picker-popup.svelte-s1wu8v .picker_done button:hover {
  background: var(--jse-button-background-highlight, #e7e7e7);
}`);var r0=L('<div class="jse-color-picker-popup svelte-s1wu8v"></div>');function a0(e,n){en(n,!1);var o=f(n,"color",8),r=f(n,"onChange",8),s=f(n,"showOnTop",8),a=E(),i=()=>{};Po(bn(function*(){var c,d=new((c=yield Wm(()=>import("./vendor-bd10311e.js").then(u=>u.bN),["assets/vendor-bd10311e.js","assets/vendor-vue-d219d204.js"]))===null||c===void 0?void 0:c.default)({parent:t(a),color:o(),popup:s()?"top":"bottom",onDone(u){var h=u.rgba[3]===1?u.hex.substring(0,7):u.hex;r()(h)}});d.show(),i=()=>{d.destroy()}})),rr(()=>{i()}),jn();var l=r0();Yt(l,c=>v(a,c),()=>t(a)),A(e,l),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-color-picker-button.svelte-xeg9n6 {
  font-size: var(--jse-font-size-mono, 14px);
  width: var(--jse-color-picker-button-size, 1em);
  height: var(--jse-color-picker-button-size, 1em);
  box-sizing: border-box;
  padding: 0;
  margin: 2px 0 0 calc(0.5 * var(--jse-padding, 10px));
  display: inline-flex;
  vertical-align: top;
  border: 1px solid var(--jse-text-color, #4d4d4d);
  border-radius: 2px;
  background: inherit;
  outline: none;
}

.jse-color-picker-button.svelte-xeg9n6:not(.jse-readonly) {
  cursor: pointer;
}`);var s0=L('<button type="button"></button>');function i0(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),{openAbsolutePopup:s}=Ma("absolute-popup"),a=f(n,"path",9),i=f(n,"value",9),l=f(n,"readOnly",9),c=f(n,"onPatch",9),d=f(n,"focus",9);function u(g){c()([{op:"replace",path:Sn(a()),value:g}]),h()}function h(){d()()}F(()=>O(i()),()=>{v(o,Pp(i()))}),F(()=>(O(l()),O(i())),()=>{v(r,l()?"Color ".concat(i()):"Click to open a color picker")}),mt(),jn(!0);var p,m=s0();be(g=>{var b;p=kn(m,1,"jse-color-picker-button svelte-xeg9n6",null,p,g),fr(m,"background: ".concat((b=t(o))!==null&&b!==void 0?b:"")),ht(m,"title",t(r)),ht(m,"aria-label",t(r))},[()=>({"jse-readonly":l()})],ae),ie("click",m,function(g){var b,j;if(!l()){var x=g.target,k=x.getBoundingClientRect().top,$=((b=(j=Ni(x))===null||j===void 0?void 0:j.innerHeight)!==null&&b!==void 0?b:0)-k<300&&k>300,S={color:i(),onChange:u,showOnTop:$};s(a0,S,{anchor:x,closeOnOuterClick:!0,onClose:h,offsetTop:18,offsetLeft:-8,height:300})}}),A(e,m),nn()}var Ec=1e3,ji=100,pu=2e4,ms=[{start:0,end:ji}],l0=1048576,bv=10485760,Ac="Insert or paste contents, enter [ insert a new array, enter { to insert a new object, or start typing to insert a new value",sd="Open context menu (Click here, right click on the selection, or use the context menu button or Ctrl+Q)",ei="hover-insert-inside",Rc="hover-insert-after",jv="hover-collection",Tc="valid",xv="repairable",Br=336,Wr=260,si=100,yv={[lr.asc]:"ascending",[lr.desc]:"descending"};function Yp(e){for(var n=$m(e,l=>l.start),o=[n[0]],r=0;r<n.length;r++){var s=o.length-1,a=o[s],i=n[r];i.start<=a.end?o[s]={start:Math.min(a.start,i.start),end:Math.max(a.end,i.end)}:o.push(i)}return o}function hu(e){return Cl(e)+ji}function Cl(e){return Math.floor(e/ji)*ji}function Kp(e){return!!e&&(e.type==="space"||e.space===!0)}function sl(e){return!!e&&(e.type==="separator"||e.separator===!0)}function c0(e){return!!e&&e.type==="label"&&typeof e.text=="string"}function ci(e){return!!e&&typeof e.onClick=="function"}function Pc(e){return!!e&&e.type==="dropdown-button"&&ci(e.main)&&Array.isArray(e.items)}function u0(e){return!!e&&e.type==="row"&&Array.isArray(e.items)}function d0(e){return!!e&&e.type==="column"&&Array.isArray(e.items)}function wv(e){return gt(e)&&gt(e.parseError)}function v0(e){return gt(e)&&Array.isArray(e.validationErrors)}function f0(e){return gt(e)&&Array.isArray(e.path)&&typeof e.message=="string"&&"severity"in e}function p0(e){return gt(e)&&f0(e)&&typeof e.isChildError=="boolean"}function h0(e){return gt(e)&&typeof e.action=="function"&&gt(e.props)}function gr(e){return e!==void 0&&e.type==="object"}function To(e){return e!==void 0&&e.type==="array"}function id(e){return e!==void 0&&e.type==="value"}function Ja(e){return gr(e)||To(e)}function kv(e){return e!==void 0&&Array.isArray(e.searchResults)}function Sl(e){return!!e&&e.type==="tree"}function Cv(e){return!!e&&e.type==="text"}function Sv(e){return!!e&&e.type==="mode"}function gu(e){var{json:n,expand:o}=e,r=function(s){var{json:a,factory:i}=s;return Array.isArray(a)?i.createArrayDocumentState():gt(a)?i.createObjectDocumentState():a!==void 0?i.createValueDocumentState():void 0}({json:n,factory:ud});return o&&r?cr(n,r,[],o):r}function ld(){var{expanded:e}=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{expanded:!1};return{type:"array",expanded:e,visibleSections:ms,items:[]}}function cd(){var{expanded:e}=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{expanded:!1};return{type:"object",expanded:e,properties:{}}}var ud={createObjectDocumentState:cd,createArrayDocumentState:ld,createValueDocumentState:function(){return{type:"value"}}};function Qp(e,n,o,r){var{createObjectDocumentState:s,createArrayDocumentState:a,createValueDocumentState:i}=r;return function l(c,d,u){if(Array.isArray(c)){var h=To(d)?d:a();if(u.length===0)return h;var p=Eo(u[0]),m=l(c[p],h.items[p],u.slice(1));return Lr(h,["items",u[0]],m)}if(gt(c)){var g=gr(d)?d:s();if(u.length===0)return g;var b=u[0],j=l(c[b],g.properties[b],u.slice(1));return Lr(g,["properties",b],j)}return id(d)?d:i()}(e,n,o)}function Yo(e,n){return xi(e,n,arguments.length>2&&arguments[2]!==void 0?arguments[2]:[],(o,r)=>{if(o!==void 0&&r!==void 0)return Array.isArray(o)?To(r)?r:ld({expanded:!!Ja(r)&&r.expanded}):gt(o)?gr(r)?r:cd({expanded:!!Ja(r)&&r.expanded}):id(r)?r:void 0},()=>!0)}function xi(e,n,o,r,s){var a=r(e,n,o);if(Array.isArray(e)&&To(a)&&s(a)){var i=[];return dd(e,a.visibleSections,c=>{var d=o.concat(String(c)),u=xi(e[c],a.items[c],d,r,s);u!==void 0&&(i[c]=u)}),vv(i,a.items)?a:me(me({},a),{},{items:i})}if(gt(e)&&gr(a)&&s(a)){var l={};return Object.keys(e).forEach(c=>{var d=o.concat(c),u=xi(e[c],a.properties[c],d,r,s);u!==void 0&&(l[c]=u)}),vv(Object.values(l),Object.values(a.properties))?a:me(me({},a),{},{properties:l})}return a}function dd(e,n,o){n.forEach(r=>{var{start:s,end:a}=r;Ip(s,Math.min(e.length,a),o)})}function yi(e,n){for(var o=e,r=[],s=0;s<n.length;){if(Array.isArray(o)){var a=n[s];r.push("items",a),o=o[Eo(a)]}else{if(!gt(o))throw new Error("Cannot convert path: Object or Array expected at index ".concat(s));var i=n[s];r.push("properties",i),o=o[i]}s++}return r}function cr(e,n,o,r){for(var s=n,a=function(l){var c=o.slice(0,l);s=zs(e,s,c,(d,u)=>{var h=Ja(u)&&!u.expanded?me(me({},u),{},{expanded:!0}):u;return To(h)?function(p,m){if(function(j,x){return j.some(k=>x>=k.start&&x<k.end)}(p.visibleSections,m))return p;var g=Cl(m),b={start:g,end:hu(g)};return me(me({},p),{},{visibleSections:Yp(p.visibleSections.concat(b))})}(h,Eo(o[l])):h})},i=0;i<o.length;i++)a(i);return zs(e,s,o,(l,c)=>function(d,u,h,p){return xi(d,u,h,(m,g,b)=>Array.isArray(m)&&p(b)?To(g)?g.expanded?g:me(me({},g),{},{expanded:!0}):ld({expanded:!0}):gt(m)&&p(b)?gr(g)?g.expanded?g:me(me({},g),{},{expanded:!0}):cd({expanded:!0}):g,m=>Ja(m)&&m.expanded)}(l,c,[],r))}function zv(e,n,o,r){return zs(e,n,o,(s,a)=>r?function(i,l,c){return xi(i,l,c,(d,u)=>Ov(u),()=>!0)}(s,a,o):Ov(a))}function Ov(e){return To(e)&&e.expanded?me(me({},e),{},{expanded:!1,visibleSections:ms}):gr(e)&&e.expanded?me(me({},e),{},{expanded:!1}):e}function Gp(e,n,o){var r={json:e,documentState:n},s=o.reduce((a,i)=>({json:zr(a.json,[i]),documentState:g0(a.json,a.documentState,i)}),r);return{json:s.json,documentState:Yo(s.json,s.documentState)}}function g0(e,n,o){if(Hf(o))return Mv(e,n,o,void 0);if(Yf(o))return $v(e,n,o);if(Ju(o)){var r=Hr(e,o.path),s=Xr(e,n,r);return s?nc(e,n,r,{type:"value",enforceString:s}):n}return eu(o)||gs(o)?function(a,i,l){if(gs(l)&&l.from===l.path)return i;var c=i,d=Hr(a,l.from),u=Ia(a,c,d);return gs(l)&&(c=$v(a,c,{path:l.from})),c=Mv(a,c,{path:l.path},u),c}(e,n,o):n}function Ia(e,n,o){try{return Je(n,yi(e,o))}catch{return}}function vd(e,n,o,r,s){var a=Qp(e,n,o,s);return xm(a,yi(e,o),i=>{var l=Je(e,o);return r(l,i)})}function nc(e,n,o,r){return function(s,a,i,l,c){var d=Qp(s,a,i,c);return Lr(d,yi(s,i),l)}(e,n,o,r,ud)}function zs(e,n,o,r){return vd(e,n,o,r,ud)}function Mv(e,n,o,r){var s=Hr(e,o.path),a=n;return a=zs(e,a,ot(s),(i,l)=>{if(!To(l))return l;var c=Eo(Kn(s)),{items:d,visibleSections:u}=l;return me(me({},l),{},{items:c<d.length?$b(d,c,r!==void 0?[r]:Array(1)):d,visibleSections:Xp(u,c,1)})}),nc(e,a,s,r)}function $v(e,n,o){var r=Hr(e,o.path),s=ot(r),a=Je(e,s);return Array.isArray(a)?zs(e,n,s,(i,l)=>{if(!To(l))return l;var c=Eo(Kn(r)),{items:d,visibleSections:u}=l;return me(me({},l),{},{items:d.slice(0,c).concat(d.slice(c+1)),visibleSections:Xp(u,c,-1)})}):function(i,l,c){var d=yi(i,c);return pa(l,d)?ym(l,yi(i,c)):l}(e,n,r)}function Xp(e,n,o){return function(r){for(var s=r.slice(0),a=1;a<s.length;)s[a-1].end===s[a].start&&(s[a-1]={start:s[a-1].start,end:s[a].end},s.splice(a)),a++;return s}(e.map(r=>({start:r.start>n?r.start+o:r.start,end:r.end>n?r.end+o:r.end})))}function Xr(e,n,o){var r,s=Je(e,o),a=Ia(e,n,o),i=id(a)?a.enforceString:void 0;return typeof i=="boolean"?i:typeof(r=s)=="string"&&typeof _s(r,JSON)!="string"}function _i(e,n){var o=arguments.length>2&&arguments[2]!==void 0&&arguments[2],r=e.indexOf(n);return r!==-1?o?e.slice(r):e.slice(r+1):[]}function fd(e,n){var o=[];return function r(s,a,i){o.push(i),wo(s)&&To(a)&&a.expanded&&dd(s,a.visibleSections,l=>{r(s[l],a.items[l],i.concat(String(l)))}),Oo(s)&&gr(a)&&a.expanded&&Object.keys(s).forEach(l=>{r(s[l],a.properties[l],i.concat(l))})}(e,n,[]),o}function Zp(e,n){var o=!(arguments.length>2&&arguments[2]!==void 0)||arguments[2],r=[];return function s(a,i){r.push({path:i,type:dr.value});var l=Ia(e,n,i);if(a&&Ja(l)&&l.expanded){if(o&&r.push({path:i,type:dr.inside}),wo(a)){var c=To(l)?l.visibleSections:ms;dd(a,c,d=>{var u=i.concat(String(d));s(a[d],u),o&&r.push({path:u,type:dr.after})})}Oo(a)&&Object.keys(a).forEach(d=>{var u=i.concat(d);r.push({path:u,type:dr.key}),s(a[d],u),o&&r.push({path:u,type:dr.after})})}}(e,[]),r}function Ic(e,n,o){var r=fd(e,n),s=r.map(Sn).indexOf(Sn(o));if(s!==-1&&s<r.length-1)return r[s+1]}function Ba(e,n,o){var r=arguments.length>3&&arguments[3]!==void 0?arguments[3]:10240;return cr(e,n,o,Ab({json:Je(e,o)},r)?ii:pd)}function qc(e,n,o){var r=Ia(e,n,o);return Ja(r)&&r.expanded?n:Ba(e,n,o)}function ii(e){return e.length===0||e.length===1&&e[0]==="0"}function Ev(e){return e.length===0}function pd(){return!0}function il(){return!1}function or(e){return e&&e.type===$t.after||!1}function Ro(e){return e&&e.type===$t.inside||!1}function Co(e){return e&&e.type===$t.key||!1}function wt(e){return e&&e.type===$t.value||!1}function Ht(e){return e&&e.type===$t.multi||!1}function tc(e){return Ht(e)&&et(e.focusPath,e.anchorPath)}function wi(e){return Ht(e)||or(e)||Ro(e)||Co(e)||wt(e)}function Nc(e){return e&&e.type===$t.text||!1}function Oa(e,n){var o=[];return function(r,s,a){if(s){var i=qa(s),l=Be(s);if(et(i,l))return a(i);if(r!==void 0){var c=nh(i,l);if(i.length===c.length||l.length===c.length)return a(c);var d=qo(i,l),u=Vr(r,d),h=ka(r,d),p=ea(r,d,u),m=ea(r,d,h);if(!(p===-1||m===-1)){var g=Je(r,c);if(Oo(g)){for(var b=Object.keys(g),j=p;j<=m;j++){var x=a(c.concat(b[j]));if(x!==void 0)return x}return}if(wo(g)){for(var k=p;k<=m;k++){var $=a(c.concat(String(k)));if($!==void 0)return $}return}throw new Error("Failed to create selection")}}}}(e,n,r=>{o.push(r)}),o}function eh(e){return Ro(e)?e.path:ot(Be(e))}function Vr(e,n){if(!Ht(n))return n.path;var o=ea(e,n,n.anchorPath);return ea(e,n,n.focusPath)<o?n.focusPath:n.anchorPath}function ka(e,n){if(!Ht(n))return n.path;var o=ea(e,n,n.anchorPath);return ea(e,n,n.focusPath)>o?n.focusPath:n.anchorPath}function Av(e,n,o){var r=arguments.length>3&&arguments[3]!==void 0&&arguments[3];if(o){var s=r?Be(o):Vr(e,o),a=function(c,d,u){var h=fd(c,d),p=h.map(Sn),m=Sn(u),g=p.indexOf(m);if(g!==-1&&g>0)return h[g-1]}(e,n,s);if(r)return Ro(o)||or(o)?a!==void 0?qo(s,s):void 0:a!==void 0?qo(qa(o),a):void 0;if(or(o)||Ro(o))return Yn(s);if(Co(o)){if(a===void 0||a.length===0)return;var i=ot(a),l=Je(e,i);return Array.isArray(l)||Mt(a)?Yn(a):ra(a)}return wt(o),a!==void 0?Yn(a):void 0}}function Rv(e,n,o,r){if(!o)return{caret:void 0,previous:void 0,next:void 0};var s=Zp(e,n,r),a=s.findIndex(i=>et(i.path,Be(o))&&String(i.type)===String(o.type));return{caret:a!==-1?s[a]:void 0,previous:a!==-1&&a>0?s[a-1]:void 0,next:a!==-1&&a<s.length-1?s[a+1]:void 0}}function rs(e,n){for(var o=fd(e,n),r=0;r<o.length-1&&o[r+1].length>o[r].length;)r++;var s=o[r];return s===void 0||s.length===0||Array.isArray(Je(e,ot(s)))?Yn(s):ra(s)}function Os(e,n){if(n.length===1){var o=Yr(n);if(o.op==="replace")return Yn(Hr(e,o.path))}if(!Mt(n)&&n.every(i=>i.op==="move")){var r=Yr(n),s=n.slice(1);if((eu(r)||gs(r))&&r.from!==r.path&&s.every(i=>(eu(i)||gs(i))&&i.from===i.path))return ra(Hr(e,r.path))}var a=n.filter(i=>i.op!=="test"&&i.op!=="remove"&&(i.op!=="move"||i.from!==i.path)&&typeof i.path=="string").map(i=>Hr(e,i.path));if(!Mt(a))return{type:$t.multi,anchorPath:Yr(a),focusPath:Kn(a)}}function nh(e,n){for(var o=0;o<e.length&&o<n.length&&e[o]===n[o];)o++;return e.slice(0,o)}function zl(e){return Co(e)||wt(e)||tc(e)}function Tv(e,n){return zl(n)&&po(Je(e,Be(n)))?Be(n):ot(Be(n))}function oa(e,n){if(e.length<n.length)return!1;for(var o=0;o<n.length;o++)if(e[o]!==n[o])return!1;return!0}function Cr(e){if(Vo(e)){var{type:n,path:o}=e;return{type:n,path:o}}return e}function ra(e){return{type:$t.key,path:e}}function hd(e,n){return{type:$t.key,path:e,edit:!0,initialValue:n}}function Yn(e){return{type:$t.value,path:e}}function Ol(e,n){return{type:$t.value,path:e,edit:!0,initialValue:n}}function aa(e){return{type:$t.inside,path:e}}function Zr(e){return{type:$t.after,path:e}}function qo(e,n){var o=nh(e,n),r=e.length>o.length&&n.length>o.length;return{type:$t.multi,anchorPath:r?o.concat(e[o.length]):o,focusPath:r?o.concat(n[o.length]):o}}function th(e,n,o,r){if(Co(n))return String(Kn(n.path));if(wt(n)){var s=Je(e,n.path);return typeof s=="string"?s:r.stringify(s,null,o)}if(Ht(n)){if(Mt(n.focusPath))return r.stringify(e,null,o);var a=eh(n),i=Je(e,a);if(Array.isArray(i)){if(tc(n)){var l=Je(e,n.focusPath);return r.stringify(l,null,o)}return Oa(e,n).map(c=>{var d=Je(e,c);return"".concat(r.stringify(d,null,o),",")}).join(`
`)}return Oa(e,n).map(c=>{var d=Kn(c),u=Je(e,c);return"".concat(r.stringify(d),": ").concat(r.stringify(u,null,o),",")}).join(`
`)}}function Vo(e){return(Co(e)||wt(e))&&e.edit===!0}function ds(e){return Co(e)||wt(e)||Ht(e)}function Qi(e){return Co(e)||wt(e)||tc(e)}function mu(e){switch(e.type){case dr.key:return ra(e.path);case dr.value:return Yn(e.path);case dr.after:return Zr(e.path);case dr.inside:return aa(e.path)}}function Pv(e,n){switch(e){case $t.key:return ra(n);case $t.value:return Yn(n);case $t.after:return Zr(n);case $t.inside:return aa(n);case $t.multi:case $t.text:return qo(n,n)}}function Iv(e,n,o){if(n)return ki(e,n,o)||oa(Ht(n)?ot(n.focusPath):n.path,o)?n:void 0}function ki(e,n,o){if(e===void 0||!n)return!1;if(Co(n)||Ro(n)||or(n))return et(n.path,o);if(wt(n))return oa(o,n.path);if(Ht(n)){var r=Vr(e,n),s=ka(e,n),a=ot(n.focusPath);if(!oa(o,a)||o.length<=a.length)return!1;var i=ea(e,n,r),l=ea(e,n,s),c=ea(e,n,o);return c!==-1&&c>=i&&c<=l}return!1}function ea(e,n,o){var r=ot(n.focusPath);if(!oa(o,r)||o.length<=r.length)return-1;var s=o[r.length],a=Je(e,r);if(Oo(a))return Object.keys(a).indexOf(s);if(wo(a)){var i=Eo(s);if(i<a.length)return i}return-1}function Be(e){return Ht(e)?e.focusPath:e.path}function qa(e){return Ht(e)?e.anchorPath:e.path}function Fs(){for(var e=[],n=arguments.length,o=new Array(n),r=0;r<n;r++)o[r]=arguments[r];for(var s of o)if(typeof s=="string"&&e.push(s),s&&typeof s=="object")for(var a in s)Object.hasOwnProperty.call(s,a)&&s[a]&&e.push(a);return e.join(" ")}function oh(e,n,o){return Fs("jse-value","jse-"+ed(e,o),{"jse-url":Xl(e),"jse-empty":typeof e=="string"&&e.length===0,"jse-table-cell":n===yo.table})}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-value.jse-string.svelte-f9kmxj {
  color: var(--jse-value-color-string, #008000);
}
.jse-value.jse-object.svelte-f9kmxj, .jse-value.jse-array.svelte-f9kmxj {
  min-width: 16px;
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
}
.jse-value.jse-number.svelte-f9kmxj {
  color: var(--jse-value-color-number, #ee422e);
}
.jse-value.jse-boolean.svelte-f9kmxj {
  color: var(--jse-value-color-boolean, #ff8c00);
}
.jse-value.jse-null.svelte-f9kmxj {
  color: var(--jse-value-color-null, #004ed0);
}
.jse-value.jse-invalid.svelte-f9kmxj {
  color: var(--jse-text-color, #4d4d4d);
}
.jse-value.jse-url.svelte-f9kmxj {
  color: var(--jse-value-color-url, #008000);
  text-decoration: underline;
}

div.jse-editable-div.svelte-f9kmxj {
  min-width: 2em;
  padding: 0 5px;
  box-sizing: border-box;
  outline: none;
  border-radius: 1px;
  vertical-align: top;
  cursor: text !important;
  word-break: normal;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
div.jse-editable-div.jse-short-text.svelte-f9kmxj {
  overflow-wrap: normal;
}
div.jse-editable-div.jse-table-cell.svelte-f9kmxj {
  overflow-wrap: normal;
  white-space: nowrap;
}
div.jse-editable-div[contenteditable=true].svelte-f9kmxj {
  outline: var(--jse-edit-outline, 2px solid #656565);
  background: var(--jse-background-color, #fff);
  position: relative;
  display: inline-block;
  border-radius: 0;
  z-index: 3;
}
div.jse-editable-div.jse-empty.svelte-f9kmxj:not(:focus) {
  outline: 1px dotted var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  -moz-outline-radius: 2px;
}
div.jse-editable-div.jse-empty.svelte-f9kmxj::after {
  pointer-events: none;
  color: var(--jse-tag-background, rgba(0, 0, 0, 0.2));
}`);var m0=L('<div role="textbox" tabindex="0" contenteditable="true" spellcheck="false"></div>');function rh(e,n){en(n,!1);var o=So("jsoneditor:EditableDiv"),r=f(n,"value",9),s=f(n,"initialValue",9),a=f(n,"shortText",9,!1),i=f(n,"label",9),l=f(n,"onChange",9),c=f(n,"onCancel",9),d=f(n,"onFind",9),u=f(n,"onPaste",9,bo),h=f(n,"onValueClass",9,()=>""),p=E(void 0,!0),m=E(void 0,!0),g=!1;function b(){return t(p)?function(k){return k.replace(/\n$/,"")}(t(p).innerText):""}function j(k){t(p)&&Ko(p,t(p).innerText=ec(k))}Po(()=>{o("onMount",{value:r(),initialValue:s()}),j(s()!==void 0?s():r()),t(p)&&function(k){if(k.firstChild!=null){var $=document.createRange(),S=window.getSelection();$.setStart(k,1),$.collapse(!0),S==null||S.removeAllRanges(),S==null||S.addRange($)}else k.focus()}(t(p))}),rr(()=>{var k=b();o("onDestroy",{closed:g,value:r(),newValue:k}),g||k===r()||l()(k,wa.no)}),F(()=>(O(h()),O(r())),()=>{v(m,h()(r()))}),mt(),jn(!0);var x=m0();Yt(x,k=>v(p,k),()=>t(p)),be(k=>{ht(x,"aria-label",i()),kn(x,1,k,"svelte-f9kmxj")},[()=>za(Fs("jse-editable-div",t(m),{"jse-short-text":a()}))],ae),ie("input",x,function(){var k=b();k===""&&j(""),v(m,h()(k))}),ie("keydown",x,function(k){k.stopPropagation();var $=ta(k);if($==="Escape"&&(k.preventDefault(),g=!0,c()()),$==="Enter"||$==="Tab"){k.preventDefault(),g=!0;var S=b();l()(S,wa.nextInside)}$==="Ctrl+F"&&(k.preventDefault(),d()(!1)),$==="Ctrl+H"&&(k.preventDefault(),d()(!0))}),ie("paste",x,function(k){if(k.stopPropagation(),u()&&k.clipboardData){var $=k.clipboardData.getData("text/plain");u()($)}}),ie("blur",x,function(){var k=document.hasFocus(),$=b();o("handleBlur",{hasFocus:k,closed:g,value:r(),newValue:$}),document.hasFocus()&&!g&&(g=!0,$!==r()&&l()($,wa.self))}),A(e,x),nn()}function b0(e,n){en(n,!1);var o=f(n,"path",9),r=f(n,"value",9),s=f(n,"selection",9),a=f(n,"mode",9),i=f(n,"parser",9),l=f(n,"normalization",9),c=f(n,"enforceString",9),d=f(n,"onPatch",9),u=f(n,"onPasteJson",9),h=f(n,"onSelect",9),p=f(n,"onFind",9),m=f(n,"focus",9),g=f(n,"findNextInside",9);function b($){return c()?$:_s($,i())}function j(){h()(Yn(o())),m()()}jn(!0);var x=ae(()=>l().escapeValue(r())),k=ae(()=>Vo(s())?s().initialValue:void 0);rh(e,{get value(){return t(x)},get initialValue(){return t(k)},label:"Edit value",onChange:function($,S){d()([{op:"replace",path:Sn(o()),value:b(l().unescapeValue($))}],(y,R,N)=>{if(!N||et(o(),Be(N)))return{state:R,selection:S===wa.nextInside?g()(o()):Yn(o())}}),m()()},onCancel:j,onPaste:function($){try{var S=i().parse($);po(S)&&u()({path:o(),contents:S,onPasteAsJson:()=>{j();var y=[{op:"replace",path:Sn(o()),value:S}];d()(y,(R,N)=>({state:Ba(R,N,o())}))}})}catch{}},get onFind(){return p()},onValueClass:function($){return oh(b(l().unescapeValue($)),a(),i())}}),nn()}function vs(e,n,o){var r=ot(n),s=Je(e,r);if(wo(s)){var a=Eo(Kn(n));return o.map((d,u)=>({op:"add",path:Sn(r.concat(String(a+u))),value:d.value}))}if(Oo(s)){var i=Kn(n),l=Object.keys(s),c=i!==void 0?_i(l,i,!0):[];return[...o.map(d=>{var u=Ui(d.key,l);return{op:"add",path:Sn(r.concat(u)),value:d.value}}),...c.map(d=>Wa(r,d))]}throw new Error("Cannot create insert operations: parent must be an Object or Array")}function bu(e,n,o){var r=Je(e,n);if(Array.isArray(r)){var s=r.length;return o.map((a,i)=>({op:"add",path:Sn(n.concat(String(s+i))),value:a.value}))}return o.map(a=>{var i=Ui(a.key,Object.keys(r));return{op:"add",path:Sn(n.concat(i)),value:a.value}})}function Di(e,n,o,r){var s=Ui(r,n.filter(i=>i!==o)),a=_i(n,o,!1);return[{op:"move",from:Sn(e.concat(o)),path:Sn(e.concat(s))},...a.map(i=>Wa(e,i))]}function ah(e,n){var o=Kn(n);if(Mt(o))throw new Error("Cannot duplicate root object");var r=ot(o),s=Kn(o),a=Je(e,r);if(wo(a)){var i=Kn(n),l=i?Eo(Kn(i))+1:0;return[...n.map((u,h)=>({op:"copy",from:Sn(u),path:Sn(r.concat(String(h+l)))}))]}if(Oo(a)){var c=Object.keys(a),d=s!==void 0?_i(c,s,!1):[];return[...n.map(u=>{var h=Ui(Kn(u),c);return{op:"copy",from:Sn(u),path:Sn(r.concat(h))}}),...d.map(u=>Wa(r,u))]}throw new Error("Cannot create duplicate operations: parent must be an Object or Array")}function sh(e,n){if(wt(n))return[{op:"move",from:Sn(n.path),path:""}];if(!Ht(n))throw new Error("Cannot create extract operations: parent must be an Object or Array");var o=ot(n.focusPath),r=Je(e,o);if(wo(r)){var s=Oa(e,n).map(i=>{var l=Eo(Kn(i));return r[l]});return[{op:"replace",path:"",value:s}]}if(Oo(r)){var a={};return Oa(e,n).forEach(i=>{var l=String(Kn(i));a[l]=r[l]}),[{op:"replace",path:"",value:a}]}throw new Error("Cannot extract: unsupported type of selection "+JSON.stringify(n))}function ih(e,n,o,r){if(Co(n)){var s=Np(o,r),a=ot(n.path),i=Je(e,a);return Di(a,Object.keys(i),Kn(n.path),typeof s=="string"?s:o)}if(wt(n)||Ht(n)&&Mt(n.focusPath))try{return[{op:"replace",path:Sn(Be(n)),value:Zl(o,R=>qi(R,r))}]}catch{return[{op:"replace",path:Sn(Be(n)),value:o}]}if(Ht(n)){var l=Uc(o,r);return function(R,N,_){var z=Yr(N),W=ot(z),X=Je(R,W);if(wo(X)){var H=Yr(N),ne=H?Eo(Kn(H)):0;return[...Ml(N),..._.map((we,Ne)=>({op:"add",path:Sn(W.concat(String(Ne+ne))),value:we.value}))]}if(Oo(X)){var ye=Kn(N),te=ot(ye),ve=Kn(ye),Re=Object.keys(X),Ee=ve!==void 0?_i(Re,ve,!1):[],fe=new Set(N.map(we=>Kn(we))),Se=Re.filter(we=>!fe.has(we));return[...Ml(N),..._.map(we=>{var Ne=Ui(we.key,Se);return{op:"add",path:Sn(te.concat(Ne)),value:we.value}}),...Ee.map(we=>Wa(te,we))]}throw new Error("Cannot create replace operations: parent must be an Object or Array")}(e,Oa(e,n),l)}if(or(n)){var c=Uc(o,r),d=n.path,u=ot(d),h=Je(e,u);if(wo(h)){var p=Eo(Kn(d));return vs(e,u.concat(String(p+1)),c)}if(Oo(h)){var m=String(Kn(d)),g=Object.keys(h);if(Mt(g)||Kn(g)===m)return bu(e,u,c);var b=g.indexOf(m),j=g[b+1];return vs(e,u.concat(j),c)}throw new Error("Cannot create insert operations: parent must be an Object or Array")}if(Ro(n)){var x=Uc(o,r),k=n.path,$=Je(e,k);if(wo($))return vs(e,k.concat("0"),x);if(Oo($)){var S=Object.keys($);if(Mt(S))return bu(e,k,x);var y=Yr(S);return vs(e,k.concat(y),x)}throw new Error("Cannot create insert operations: parent must be an Object or Array")}throw new Error("Cannot insert: unsupported type of selection "+JSON.stringify(n))}function Ml(e){return e.map(n=>({op:"remove",path:Sn(n)})).reverse()}function Wa(e,n){return{op:"move",from:Sn(e.concat(n)),path:Sn(e.concat(n))}}function Uc(e,n){var o=/^\s*{/.test(e),r=/^\s*\[/.test(e),s=Np(e,n),a=s!==void 0?s:Zl(e,i=>qi(i,n));return o&&gt(a)||r&&Array.isArray(a)?[{key:"New item",value:a}]:Array.isArray(a)?a.map((i,l)=>({key:"New item "+l,value:i})):gt(a)?Object.keys(a).map(i=>({key:i,value:a[i]})):[{key:"New item",value:a}]}function lh(e,n){if(Co(n)){var o=ot(n.path),r=Je(e,o),s=Di(o,Object.keys(r),Kn(n.path),"");return{operations:s,newSelection:Os(e,s)}}if(wt(n))return{operations:[{op:"replace",path:Sn(n.path),value:""}],newSelection:n};if(Ht(n)){var a=Oa(e,n),i=Ml(a),l=Kn(a);if(Mt(l))return{operations:[{op:"replace",path:"",value:""}],newSelection:Yn([])};var c=ot(l),d=Je(e,c);if(wo(d)){var u=Yr(a),h=Eo(Kn(u));return{operations:i,newSelection:h===0?aa(c):Zr(c.concat(String(h-1)))}}if(Oo(d)){var p=Object.keys(d),m=Yr(a),g=Kn(m),b=p.indexOf(g),j=p[b-1];return{operations:i,newSelection:b===0?aa(c):Zr(c.concat(j))}}throw new Error("Cannot create remove operations: parent must be an Object or Array")}throw new Error("Cannot remove: unsupported type of selection "+JSON.stringify(n))}function ch(e,n){return function(o){var r=arguments.length>1&&arguments[1]!==void 0?arguments[1]:et;return o.filter((s,a)=>{for(var i=a+1;i<o.length;i++)if(r(s,o[i]))return!1;return!0})}(Ff(e,n,{before:(o,r,s)=>{if(Yf(r)){var a=Sr(r.path);return{revertOperations:[...s,..._c(o,a)]}}if(gs(r)){var i=Sr(r.from);return{revertOperations:r.from===r.path?[r,..._c(o,i)]:[...s,..._c(o,i)]}}return{document:o}}}))}function _c(e,n){var o=ot(n),r=Kn(n),s=Je(e,o);return Oo(s)?_i(Object.keys(s),r,!1).map(a=>Wa(o,a)):[]}function qv(e){var n=e.activeIndex<e.items.length-1?e.activeIndex+1:e.items.length>0?0:-1,o=e.items[n],r=e.items.map((s,a)=>me(me({},s),{},{active:a===n}));return me(me({},e),{},{items:r,activeItem:o,activeIndex:n})}function Nv(e,n){var o,r=arguments.length>2&&arguments[2]!==void 0?arguments[2]:{},s=e.toLowerCase(),a=(o=r==null?void 0:r.maxResults)!==null&&o!==void 0?o:1/0,i=r==null?void 0:r.columns,l=[],c=[];function d(j){l.length>=a||l.push(j)}function u(j,x){if(wo(x)){var k=c.length;c.push("0");for(var $=0;$<x.length;$++)if(c[k]=String($),u(j,x[$]),l.length>=a)return;c.pop()}else if(Oo(x)){var S=Object.keys(x),y=c.length;for(var R of(c.push(""),S))if(c[y]=R,Uv(R,j,c,hr.key,d),u(j,x[R]),l.length>=a)return;c.pop()}else Uv(String(x),j,c,hr.value,d)}if(e==="")return[];if(i){if(!Array.isArray(n))throw new Error("json must be an Array when option columns is defined");for(var h=0;h<n.length;h++){c[0]=String(h);for(var p=n[h],m=0;m<i.length;m++){var g=i[m];if(g.length===1)c[1]=g[0];else for(var b=0;b<g.length;b++)c[b+1]=g[b];for(;c.length>g.length+1;)c.pop();u(s,Je(p,g))}if(l.length>=a)break}return l}return u(s,n),l}function Uv(e,n,o,r,s){var a=e.toLowerCase(),i=0,l=-1,c=-1;do(c=a.indexOf(n,l))!==-1&&(l=c+n.length,s({path:o.slice(0),field:r,fieldIndex:i,start:c,end:l}),i++);while(c!==-1)}function ju(e,n,o,r){return e.substring(0,o)+n+e.substring(r)}function _v(e,n,o){var r=e;return Pm(o,s=>{r=ju(r,n,s.start,s.end)}),r}function j0(e,n,o,r,s){var{field:a,path:i,start:l,end:c}=r;if(a===hr.key){var d=ot(i),u=Je(e,d),h=Kn(i),p=Di(d,Object.keys(u),h,ju(h,o,l,c));return{newSelection:Os(e,p),operations:p}}if(a===hr.value){var m=Je(e,i);if(m===void 0)throw new Error("Cannot replace: path not found ".concat(Sn(i)));var g=typeof m=="string"?m:String(m),b=Xr(e,n,i),j=ju(g,o,l,c),x=[{op:"replace",path:Sn(i),value:b?j:_s(j,s)}];return{newSelection:Os(e,x),operations:x}}throw new Error("Cannot replace: unknown type of search result field ".concat(a))}function Dv(e){return e.path.concat(e.field,String(e.fieldIndex))}var x0={createObjectDocumentState:()=>({type:"object",properties:{}}),createArrayDocumentState:()=>({type:"array",items:[]}),createValueDocumentState:()=>({type:"value"})};function uh(e,n){return n.reduce((o,r)=>function(s,a,i,l){return vd(s,a,i,l,x0)}(e,o,r.path,(s,a)=>me(me({},a),{},{searchResults:a.searchResults?a.searchResults.concat(r):[r]})),void 0)}function xu(e){var n,o=(n=e==null?void 0:e.searchResults)!==null&&n!==void 0?n:[],r=gr(e)?Object.values(e.properties).flatMap(xu):To(e)?e.items.flatMap(xu):[];return o.concat(r)}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-highlight.svelte-5fb7bl {
  background-color: var(--jse-search-match-color, #ffe665);
  outline: var(--jse-search-match-outline, none);
}
.jse-highlight.jse-active.svelte-5fb7bl {
  background-color: var(--jse-search-match-active-color, var(--jse-search-match-color, #ffe665));
  outline: var(--jse-search-match-outline, 2px solid #e0be00);
}`);var y0=L("<span> </span>");function dh(e,n){en(n,!1);var o=E(),r=f(n,"text",8),s=f(n,"searchResultItems",8);F(()=>(O(r()),O(s())),()=>{v(o,function(i,l){var c=[],d=0;for(var u of l){var h=i.slice(d,u.start);h!==""&&c.push({resultIndex:void 0,type:"normal",text:h,active:!1});var p=i.slice(u.start,u.end);c.push({resultIndex:u.resultIndex,type:"highlight",text:p,active:u.active}),d=u.end}var m=Kn(l);return m&&m.end<i.length&&c.push({type:"normal",text:i.slice(m.end),resultIndex:void 0,active:!1}),c}(String(r()),s()))}),mt(),jn();var a=to();lo(rn(a),1,()=>t(o),ho,(i,l)=>{var c=to(),d=rn(c),u=p=>{var m=zo();be(()=>ln(m,t(l).text)),A(p,m)},h=p=>{var m,g=y0(),b=M(g);be((j,x,k)=>{m=kn(g,1,"jse-highlight svelte-5fb7bl",null,m,j),ht(g,"data-search-result-index",x),ln(b,k)},[()=>({"jse-active":t(l).active}),()=>String(t(l).resultIndex),()=>ec(t(l).text)],ae),A(p,g)};Q(d,p=>{t(l).type==="normal"?p(u):p(h,!1)}),A(i,c)}),A(e,a),nn()}function yu(e){var n=1e3;if(e<900)return e.toFixed()+" B";var o=e/n;if(o<900)return o.toFixed(1)+" KB";var r=o/n;if(r<900)return r.toFixed(1)+" MB";var s=r/n;return s<900?s.toFixed(1)+" GB":(s/n).toFixed(1)+" TB"}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-tag.svelte-tqwlgz {
  border: none;
  font-size: 80%;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  color: var(--jse-tag-color, var(--jse-text-color-inverse, #fff));
  background: var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  border-radius: 2px;
  cursor: pointer;
  display: inline-block;
  padding: 0 4px;
  line-height: normal;
  margin: 1px 0;
}
.jse-tag.svelte-tqwlgz:hover {
  opacity: 0.8;
}
.jse-tag.svelte-tqwlgz:disabled {
  opacity: 0.7;
  cursor: inherit;
}`);var w0=L('<button type="button" class="jse-tag svelte-tqwlgz"><!></button>');function ll(e,n){en(n,!0);var o=Wo(()=>n.onclick?s=>{s.preventDefault(),s.stopPropagation(),n.onclick()}:void 0),r=w0();r.__click=function(){for(var s,a=arguments.length,i=new Array(a),l=0;l<a;l++)i[l]=arguments[l];(s=t(o))===null||s===void 0||s.apply(this,i)},function(s,a){for(var i=arguments.length,l=new Array(i>2?i-2:0),c=2;c<i;c++)l[c-2]=arguments[c];var d,u=s,h=Gd;Ya(()=>{h!==(h=a())&&(d&&(Tr(d),d=null),d=na(()=>h(u,...l)))},Ai)}(M(r),()=>{var s;return(s=n.children)!==null&&s!==void 0?s:Gd}),be(()=>r.disabled=!n.onclick),A(e,r),nn()}Ii(["click"]);function k0(e,n,o){typeof n.value=="string"&&t(o)&&ad(e)&&(e.preventDefault(),e.stopPropagation(),window.open(n.value,"_blank"))}function C0(e,n){n.readOnly||(e.preventDefault(),n.onSelect(Ol(n.path)))}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-value.jse-string.svelte-c0g9qz {
  color: var(--jse-value-color-string, #008000);
}
.jse-value.jse-object.svelte-c0g9qz, .jse-value.jse-array.svelte-c0g9qz {
  min-width: 16px;
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
}
.jse-value.jse-number.svelte-c0g9qz {
  color: var(--jse-value-color-number, #ee422e);
}
.jse-value.jse-boolean.svelte-c0g9qz {
  color: var(--jse-value-color-boolean, #ff8c00);
}
.jse-value.jse-null.svelte-c0g9qz {
  color: var(--jse-value-color-null, #004ed0);
}
.jse-value.jse-invalid.svelte-c0g9qz {
  color: var(--jse-text-color, #4d4d4d);
}
.jse-value.jse-url.svelte-c0g9qz {
  color: var(--jse-value-color-url, #008000);
  text-decoration: underline;
}

.jse-value.svelte-c0g9qz {
  display: inline-block;
  min-width: 2em;
  padding: 0 5px;
  box-sizing: border-box;
  outline: none;
  border-radius: 1px;
  vertical-align: top;
  word-break: normal;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.jse-value.jse-table-cell.svelte-c0g9qz {
  overflow-wrap: normal;
  white-space: nowrap;
}
.jse-value.jse-empty.svelte-c0g9qz {
  min-width: 4em;
  outline: 1px dotted var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  -moz-outline-radius: 2px;
}
.jse-value.jse-empty.svelte-c0g9qz::after {
  pointer-events: none;
  color: var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  content: "value";
}`);var S0=L('<div role="button" tabindex="-1" data-type="selectable-value"><!> <!></div>');function z0(e,n){en(n,!0);var o=Fr(!0),r=Wo(()=>t(o)&&typeof n.value=="string"&&n.value.length>n.truncateTextSize&&(!n.searchResultItems||!n.searchResultItems.some(m=>m.active&&m.end>n.truncateTextSize))),s=Wo(()=>t(r)&&typeof n.value=="string"?n.value.substring(0,n.truncateTextSize).trim():n.value),a=Wo(()=>Xl(n.value));function i(){v(o,!1)}var l=S0();l.__click=[k0,n,a],l.__dblclick=[C0,n];var c=M(l),d=m=>{var g=Wo(()=>n.normalization.escapeValue(t(s)));dh(m,{get text(){return t(g)},get searchResultItems(){return n.searchResultItems}})},u=m=>{var g=zo();be(b=>ln(g,b),[()=>ec(n.normalization.escapeValue(t(s)))]),A(m,g)};Q(c,m=>{n.searchResultItems?m(d):m(u,!1)});var h=q(c,2),p=m=>{ll(m,{onclick:i,children:(g,b)=>{var j=zo();be(x=>ln(j,"Show more (".concat(x??"",")")),[()=>yu(n.value.length)]),A(g,j)},$$slots:{default:!0}})};Q(h,m=>{t(r)&&typeof n.value=="string"&&m(p)}),be(m=>{kn(l,1,m,"svelte-c0g9qz"),ht(l,"title",t(a)?"Ctrl+Click or Ctrl+Enter to open url in new window":void 0)},[()=>za(oh(n.value,n.mode,n.parser))]),A(e,l),nn()}Ii(["click","dblclick"]);fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-tooltip.svelte-14y3y8t {
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  line-height: normal;
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
  border-radius: 3px;
  background: var(--jse-context-menu-background, #656565);
  color: var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff));
  white-space: nowrap;
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
}`);var O0=L('<div class="jse-tooltip svelte-14y3y8t"> </div>');function M0(e,n){var o=f(n,"text",8),r=O0(),s=M(r);be(()=>ln(s,o())),A(e,r)}function Ms(e,n){var o,{text:r,openAbsolutePopup:s,closeAbsolutePopup:a}=n;function i(){o=s(M0,{text:r},{position:"top",width:10*r.length,offsetTop:3,anchor:e,closeOnOuterClick:!0})}function l(){a(o)}return e.addEventListener("mouseenter",i),e.addEventListener("mouseleave",l),{destroy(){e.removeEventListener("mouseenter",i),e.removeEventListener("mouseleave",l)}}}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-timestamp.svelte-1jla5ec {
  padding: 0;
  margin: 0;
  vertical-align: middle;
  display: inline-flex;
  color: var(--jse-value-color-number, #ee422e);
}`);var $0=L('<div class="jse-timestamp svelte-1jla5ec"><!></div>');function E0(e,n){en(n,!1);var o=E(void 0,!0),r=Ma("absolute-popup"),s=f(n,"value",9);F(()=>O(s()),()=>{v(o,"Time: ".concat(new Date(s()).toString()))}),mt(),jn(!0);var a=$0();nt(M(a),{data:Eg}),Uo(a,(i,l)=>Ms==null?void 0:Ms(i,l),()=>me({text:t(o)},r)),A(e,a),nn()}function A0(e){var n=[];return!e.isEditing&&Sb(e.value)&&n.push({component:o0,props:e}),!e.isEditing&&zb(e.value)&&n.push({component:i0,props:e}),e.isEditing&&n.push({component:b0,props:e}),e.isEditing||n.push({component:z0,props:e}),!e.isEditing&&cu(e.value)&&n.push({component:E0,props:e}),n}function vr(e){return e.map((n,o)=>T0.test(n)?"["+n+"]":/[.[\]]/.test(n)||n===""?'["'+function(r){return r.replace(/"/g,'\\"')}(n)+'"]':(o>0?".":"")+n).join("")}function R0(e){for(var n=[],o=0;o<e.length;)e[o]==="."&&o++,e[o]==="["?(o++,e[o]==='"'?(o++,n.push(r(a=>a==='"',!0)),s('"')):n.push(r(a=>a==="]")),s("]")):n.push(r(a=>a==="."||a==="["));function r(a){for(var i=arguments.length>1&&arguments[1]!==void 0&&arguments[1],l="";o<e.length&&!a(e[o]);)i&&e[o]==="\\"&&e[o+1]==='"'?(l+='"',o+=2):(l+=e[o],o++);return l}function s(a){if(e[o]!==a)throw new SyntaxError("Invalid JSON path: ".concat(a," expected at position ").concat(o));o++}return n}function fa(e){return{value:e,label:Mt(e)?"(item root)":vr(e)}}var T0=/^\d+$/,P0={},I0={showWizard:!0,showOriginal:!0},$l=Math.min,Na=Math.max,El=Math.round,Gi=Math.floor,$r=e=>({x:e,y:e}),q0={left:"right",right:"left",bottom:"top",top:"bottom"},N0={start:"end",end:"start"};function Fv(e,n,o){return Na(e,$l(n,o))}function oc(e,n){return typeof e=="function"?e(n):e}function Ua(e){return e.split("-")[0]}function Al(e){return e.split("-")[1]}function vh(e){return e==="x"?"y":"x"}function fh(e){return e==="y"?"height":"width"}function $s(e){return["top","bottom"].includes(Ua(e))?"y":"x"}function ph(e){return vh($s(e))}function Dc(e){return e.replace(/start|end/g,n=>N0[n])}function Xi(e){return e.replace(/left|right|bottom|top/g,n=>q0[n])}function U0(e){return typeof e!="number"?function(n){return me({top:0,right:0,bottom:0,left:0},n)}(e):{top:e,right:e,bottom:e,left:e}}function Rl(e){var{x:n,y:o,width:r,height:s}=e;return{width:r,height:s,top:o,left:n,right:n+r,bottom:o+s,x:n,y:o}}function Lv(e,n,o){var r,{reference:s,floating:a}=e,i=$s(n),l=ph(n),c=fh(l),d=Ua(n),u=i==="y",h=s.x+s.width/2-a.width/2,p=s.y+s.height/2-a.height/2,m=s[c]/2-a[c]/2;switch(d){case"top":r={x:h,y:s.y-a.height};break;case"bottom":r={x:h,y:s.y+s.height};break;case"right":r={x:s.x+s.width,y:p};break;case"left":r={x:s.x-a.width,y:p};break;default:r={x:s.x,y:s.y}}switch(Al(n)){case"start":r[l]-=m*(o&&u?-1:1);break;case"end":r[l]+=m*(o&&u?-1:1)}return r}var _0=function(){var e=bn(function*(n,o,r){for(var{placement:s="bottom",strategy:a="absolute",middleware:i=[],platform:l}=r,c=i.filter(Boolean),d=yield l.isRTL==null?void 0:l.isRTL(o),u=yield l.getElementRects({reference:n,floating:o,strategy:a}),{x:h,y:p}=Lv(u,s,d),m=s,g={},b=0,j=0;j<c.length;j++){var{name:x,fn:k}=c[j],{x:$,y:S,data:y,reset:R}=yield k({x:h,y:p,initialPlacement:s,placement:m,strategy:a,middlewareData:g,rects:u,platform:l,elements:{reference:n,floating:o}});h=$??h,p=S??p,g=me(me({},g),{},{[x]:me(me({},g[x]),y)}),R&&b<=50&&(b++,typeof R=="object"&&(R.placement&&(m=R.placement),R.rects&&(u=R.rects===!0?yield l.getElementRects({reference:n,floating:o,strategy:a}):R.rects),{x:h,y:p}=Lv(u,m,d)),j=-1)}return{x:h,y:p,placement:m,strategy:a,middlewareData:g}});return function(n,o,r){return e.apply(this,arguments)}}();function hh(e,n){return wu.apply(this,arguments)}function wu(){return wu=bn(function*(e,n){var o;n===void 0&&(n={});var{x:r,y:s,platform:a,rects:i,elements:l,strategy:c}=e,{boundary:d="clippingAncestors",rootBoundary:u="viewport",elementContext:h="floating",altBoundary:p=!1,padding:m=0}=oc(n,e),g=U0(m),b=l[p?h==="floating"?"reference":"floating":h],j=Rl(yield a.getClippingRect({element:(o=yield a.isElement==null?void 0:a.isElement(b))==null||o?b:b.contextElement||(yield a.getDocumentElement==null?void 0:a.getDocumentElement(l.floating)),boundary:d,rootBoundary:u,strategy:c})),x=h==="floating"?{x:r,y:s,width:i.floating.width,height:i.floating.height}:i.reference,k=yield a.getOffsetParent==null?void 0:a.getOffsetParent(l.floating),$=(yield a.isElement==null?void 0:a.isElement(k))&&(yield a.getScale==null?void 0:a.getScale(k))||{x:1,y:1},S=Rl(a.convertOffsetParentRelativeRectToViewportRelativeRect?yield a.convertOffsetParentRelativeRectToViewportRelativeRect({elements:l,rect:x,offsetParent:k,strategy:c}):x);return{top:(j.top-S.top+g.top)/$.y,bottom:(S.bottom-j.bottom+g.bottom)/$.y,left:(j.left-S.left+g.left)/$.x,right:(S.right-j.right+g.right)/$.x}}),wu.apply(this,arguments)}function ku(){return ku=bn(function*(e,n){var{placement:o,platform:r,elements:s}=e,a=yield r.isRTL==null?void 0:r.isRTL(s.floating),i=Ua(o),l=Al(o),c=$s(o)==="y",d=["left","top"].includes(i)?-1:1,u=a&&c?-1:1,h=oc(n,e),{mainAxis:p,crossAxis:m,alignmentAxis:g}=typeof h=="number"?{mainAxis:h,crossAxis:0,alignmentAxis:null}:{mainAxis:h.mainAxis||0,crossAxis:h.crossAxis||0,alignmentAxis:h.alignmentAxis};return l&&typeof g=="number"&&(m=l==="end"?-1*g:g),c?{x:m*u,y:p*d}:{x:p*d,y:m*u}}),ku.apply(this,arguments)}function rc(){return typeof window<"u"}function Es(e){return gh(e)?(e.nodeName||"").toLowerCase():"#document"}function Go(e){var n;return(e==null||(n=e.ownerDocument)==null?void 0:n.defaultView)||window}function Er(e){var n;return(n=(gh(e)?e.ownerDocument:e.document)||window.document)==null?void 0:n.documentElement}function gh(e){return!!rc()&&(e instanceof Node||e instanceof Go(e).Node)}function mr(e){return!!rc()&&(e instanceof Element||e instanceof Go(e).Element)}function Pr(e){return!!rc()&&(e instanceof HTMLElement||e instanceof Go(e).HTMLElement)}function Jv(e){return!(!rc()||typeof ShadowRoot>"u")&&(e instanceof ShadowRoot||e instanceof Go(e).ShadowRoot)}function Ci(e){var{overflow:n,overflowX:o,overflowY:r,display:s}=br(e);return/auto|scroll|overlay|hidden|clip/.test(n+r+o)&&!["inline","contents"].includes(s)}function D0(e){return["table","td","th"].includes(Es(e))}function Tl(e){return[":popover-open",":modal"].some(n=>{try{return e.matches(n)}catch{return!1}})}function Cu(e){var n=gd(),o=mr(e)?br(e):e;return["transform","translate","scale","rotate","perspective"].some(r=>!!o[r]&&o[r]!=="none")||!!o.containerType&&o.containerType!=="normal"||!n&&!!o.backdropFilter&&o.backdropFilter!=="none"||!n&&!!o.filter&&o.filter!=="none"||["transform","translate","scale","rotate","perspective","filter"].some(r=>(o.willChange||"").includes(r))||["paint","layout","strict","content"].some(r=>(o.contain||"").includes(r))}function gd(){return!(typeof CSS>"u"||!CSS.supports)&&CSS.supports("-webkit-backdrop-filter","none")}function bs(e){return["html","body","#document"].includes(Es(e))}function br(e){return Go(e).getComputedStyle(e)}function ac(e){return mr(e)?{scrollLeft:e.scrollLeft,scrollTop:e.scrollTop}:{scrollLeft:e.scrollX,scrollTop:e.scrollY}}function ba(e){if(Es(e)==="html")return e;var n=e.assignedSlot||e.parentNode||Jv(e)&&e.host||Er(e);return Jv(n)?n.host:n}function mh(e){var n=ba(e);return bs(n)?e.ownerDocument?e.ownerDocument.body:e.body:Pr(n)&&Ci(n)?n:mh(n)}function Si(e,n,o){var r;n===void 0&&(n=[]),o===void 0&&(o=!0);var s=mh(e),a=s===((r=e.ownerDocument)==null?void 0:r.body),i=Go(s);if(a){var l=Su(i);return n.concat(i,i.visualViewport||[],Ci(s)?s:[],l&&o?Si(l):[])}return n.concat(s,Si(s,[],o))}function Su(e){return e.parent&&Object.getPrototypeOf(e.parent)?e.frameElement:null}function bh(e){var n=br(e),o=parseFloat(n.width)||0,r=parseFloat(n.height)||0,s=Pr(e),a=s?e.offsetWidth:o,i=s?e.offsetHeight:r,l=El(o)!==a||El(r)!==i;return l&&(o=a,r=i),{width:o,height:r,$:l}}function md(e){return mr(e)?e:e.contextElement}function js(e){var n=md(e);if(!Pr(n))return $r(1);var o=n.getBoundingClientRect(),{width:r,height:s,$:a}=bh(n),i=(a?El(o.width):o.width)/r,l=(a?El(o.height):o.height)/s;return i&&Number.isFinite(i)||(i=1),l&&Number.isFinite(l)||(l=1),{x:i,y:l}}var F0=$r(0);function jh(e){var n=Go(e);return gd()&&n.visualViewport?{x:n.visualViewport.offsetLeft,y:n.visualViewport.offsetTop}:F0}function Va(e,n,o,r){n===void 0&&(n=!1),o===void 0&&(o=!1);var s=e.getBoundingClientRect(),a=md(e),i=$r(1);n&&(r?mr(r)&&(i=js(r)):i=js(e));var l=function(y,R,N){return R===void 0&&(R=!1),!(!N||R&&N!==Go(y))&&R}(a,o,r)?jh(a):$r(0),c=(s.left+l.x)/i.x,d=(s.top+l.y)/i.y,u=s.width/i.x,h=s.height/i.y;if(a)for(var p=Go(a),m=r&&mr(r)?Go(r):r,g=p,b=Su(g);b&&r&&m!==g;){var j=js(b),x=b.getBoundingClientRect(),k=br(b),$=x.left+(b.clientLeft+parseFloat(k.paddingLeft))*j.x,S=x.top+(b.clientTop+parseFloat(k.paddingTop))*j.y;c*=j.x,d*=j.y,u*=j.x,h*=j.y,c+=$,d+=S,b=Su(g=Go(b))}return Rl({width:u,height:h,x:c,y:d})}function bd(e,n){var o=ac(e).scrollLeft;return n?n.left+o:Va(Er(e)).left+o}function xh(e,n,o){o===void 0&&(o=!1);var r=e.getBoundingClientRect();return{x:r.left+n.scrollLeft-(o?0:bd(e,r)),y:r.top+n.scrollTop}}function Bv(e,n,o){var r;if(n==="viewport")r=function(a,i){var l=Go(a),c=Er(a),d=l.visualViewport,u=c.clientWidth,h=c.clientHeight,p=0,m=0;if(d){u=d.width,h=d.height;var g=gd();(!g||g&&i==="fixed")&&(p=d.offsetLeft,m=d.offsetTop)}return{width:u,height:h,x:p,y:m}}(e,o);else if(n==="document")r=function(a){var i=Er(a),l=ac(a),c=a.ownerDocument.body,d=Na(i.scrollWidth,i.clientWidth,c.scrollWidth,c.clientWidth),u=Na(i.scrollHeight,i.clientHeight,c.scrollHeight,c.clientHeight),h=-l.scrollLeft+bd(a),p=-l.scrollTop;return br(c).direction==="rtl"&&(h+=Na(i.clientWidth,c.clientWidth)-d),{width:d,height:u,x:h,y:p}}(Er(e));else if(mr(n))r=function(a,i){var l=Va(a,!0,i==="fixed"),c=l.top+a.clientTop,d=l.left+a.clientLeft,u=Pr(a)?js(a):$r(1);return{width:a.clientWidth*u.x,height:a.clientHeight*u.y,x:d*u.x,y:c*u.y}}(n,o);else{var s=jh(e);r={x:n.x-s.x,y:n.y-s.y,width:n.width,height:n.height}}return Rl(r)}function yh(e,n){var o=ba(e);return!(o===n||!mr(o)||bs(o))&&(br(o).position==="fixed"||yh(o,n))}function L0(e,n,o){var r=Pr(n),s=Er(n),a=o==="fixed",i=Va(e,!0,a,n),l={scrollLeft:0,scrollTop:0},c=$r(0);if(r||!r&&!a)if((Es(n)!=="body"||Ci(s))&&(l=ac(n)),r){var d=Va(n,!0,a,n);c.x=d.x+n.clientLeft,c.y=d.y+n.clientTop}else s&&(c.x=bd(s));var u=!s||r||a?$r(0):xh(s,l);return{x:i.left+l.scrollLeft-c.x-u.x,y:i.top+l.scrollTop-c.y-u.y,width:i.width,height:i.height}}function Fc(e){return br(e).position==="static"}function Wv(e,n){if(!Pr(e)||br(e).position==="fixed")return null;if(n)return n(e);var o=e.offsetParent;return Er(e)===o&&(o=o.ownerDocument.body),o}function Vv(e,n){var o=Go(e);if(Tl(e))return o;if(!Pr(e)){for(var r=ba(e);r&&!bs(r);){if(mr(r)&&!Fc(r))return r;r=ba(r)}return o}for(var s=Wv(e,n);s&&D0(s)&&Fc(s);)s=Wv(s,n);return s&&bs(s)&&Fc(s)&&!Cu(s)?o:s||function(a){for(var i=ba(a);Pr(i)&&!bs(i);){if(Cu(i))return i;if(Tl(i))return null;i=ba(i)}return null}(e)||o}var J0={convertOffsetParentRelativeRectToViewportRelativeRect:function(e){var{elements:n,rect:o,offsetParent:r,strategy:s}=e,a=s==="fixed",i=Er(r),l=!!n&&Tl(n.floating);if(r===i||l&&a)return o;var c={scrollLeft:0,scrollTop:0},d=$r(1),u=$r(0),h=Pr(r);if((h||!h&&!a)&&((Es(r)!=="body"||Ci(i))&&(c=ac(r)),Pr(r))){var p=Va(r);d=js(r),u.x=p.x+r.clientLeft,u.y=p.y+r.clientTop}var m=!i||h||a?$r(0):xh(i,c,!0);return{width:o.width*d.x,height:o.height*d.y,x:o.x*d.x-c.scrollLeft*d.x+u.x+m.x,y:o.y*d.y-c.scrollTop*d.y+u.y+m.y}},getDocumentElement:Er,getClippingRect:function(e){var{element:n,boundary:o,rootBoundary:r,strategy:s}=e,a=[...o==="clippingAncestors"?Tl(n)?[]:function(c,d){var u=d.get(c);if(u)return u;for(var h=Si(c,[],!1).filter(x=>mr(x)&&Es(x)!=="body"),p=null,m=br(c).position==="fixed",g=m?ba(c):c;mr(g)&&!bs(g);){var b=br(g),j=Cu(g);j||b.position!=="fixed"||(p=null),(m?!j&&!p:!j&&b.position==="static"&&p&&["absolute","fixed"].includes(p.position)||Ci(g)&&!j&&yh(c,g))?h=h.filter(x=>x!==g):p=b,g=ba(g)}return d.set(c,h),h}(n,this._c):[].concat(o),r],i=a[0],l=a.reduce((c,d)=>{var u=Bv(n,d,s);return c.top=Na(u.top,c.top),c.right=$l(u.right,c.right),c.bottom=$l(u.bottom,c.bottom),c.left=Na(u.left,c.left),c},Bv(n,i,s));return{width:l.right-l.left,height:l.bottom-l.top,x:l.left,y:l.top}},getOffsetParent:Vv,getElementRects:function(){var e=bn(function*(n){var o=this.getOffsetParent||Vv,r=this.getDimensions,s=yield r(n.floating);return{reference:L0(n.reference,yield o(n.floating),n.strategy),floating:{x:0,y:0,width:s.width,height:s.height}}});return function(n){return e.apply(this,arguments)}}(),getClientRects:function(e){return Array.from(e.getClientRects())},getDimensions:function(e){var{width:n,height:o}=bh(e);return{width:n,height:o}},getScale:js,isElement:mr,isRTL:function(e){return br(e).direction==="rtl"}};function Hv(e,n){return e.x===n.x&&e.y===n.y&&e.width===n.width&&e.height===n.height}function B0(e,n,o,r){r===void 0&&(r={});var{ancestorScroll:s=!0,ancestorResize:a=!0,elementResize:i=typeof ResizeObserver=="function",layoutShift:l=typeof IntersectionObserver=="function",animationFrame:c=!1}=r,d=md(e),u=s||a?[...d?Si(d):[],...Si(n)]:[];u.forEach(j=>{s&&j.addEventListener("scroll",o,{passive:!0}),a&&j.addEventListener("resize",o)});var h,p=d&&l?function(j,x){var k,$=null,S=Er(j);function y(){var R;clearTimeout(k),(R=$)==null||R.disconnect(),$=null}return function R(N,_){N===void 0&&(N=!1),_===void 0&&(_=1),y();var z=j.getBoundingClientRect(),{left:W,top:X,width:H,height:ne}=z;if(N||x(),H&&ne){var ye={rootMargin:-Gi(X)+"px "+-Gi(S.clientWidth-(W+H))+"px "+-Gi(S.clientHeight-(X+ne))+"px "+-Gi(W)+"px",threshold:Na(0,$l(1,_))||1},te=!0;try{$=new IntersectionObserver(ve,me(me({},ye),{},{root:S.ownerDocument}))}catch{$=new IntersectionObserver(ve,ye)}$.observe(j)}function ve(Re){var Ee=Re[0].intersectionRatio;if(Ee!==_){if(!te)return R();Ee?R(!1,Ee):k=setTimeout(()=>{R(!1,1e-7)},1e3)}Ee!==1||Hv(z,j.getBoundingClientRect())||R(),te=!1}}(!0),y}(d,o):null,m=-1,g=null;i&&(g=new ResizeObserver(j=>{var[x]=j;x&&x.target===d&&g&&(g.unobserve(n),cancelAnimationFrame(m),m=requestAnimationFrame(()=>{var k;(k=g)==null||k.observe(n)})),o()}),d&&!c&&g.observe(d),g.observe(n));var b=c?Va(e):null;return c&&function j(){var x=Va(e);b&&!Hv(b,x)&&o(),b=x,h=requestAnimationFrame(j)}(),o(),()=>{var j;u.forEach(x=>{s&&x.removeEventListener("scroll",o),a&&x.removeEventListener("resize",o)}),p==null||p(),(j=g)==null||j.disconnect(),g=null,c&&cancelAnimationFrame(h)}}var W0=function(e){return e===void 0&&(e=0),{name:"offset",options:e,fn:n=>bn(function*(){var o,r,{x:s,y:a,placement:i,middlewareData:l}=n,c=yield function(d,u){return ku.apply(this,arguments)}(n,e);return i===((o=l.offset)==null?void 0:o.placement)&&(r=l.arrow)!=null&&r.alignmentOffset?{}:{x:s+c.x,y:a+c.y,data:me(me({},c),{},{placement:i})}})()}},V0=function(e){return e===void 0&&(e={}),{name:"shift",options:e,fn:n=>bn(function*(){var{x:o,y:r,placement:s}=n,a=oc(e,n),{mainAxis:i=!0,crossAxis:l=!1,limiter:c={fn:$=>{var{x:S,y}=$;return{x:S,y}}}}=a,d=Gf(a,Hm),u={x:o,y:r},h=yield hh(n,d),p=$s(Ua(s)),m=vh(p),g=u[m],b=u[p];if(i){var j=m==="y"?"bottom":"right";g=Fv(g+h[m==="y"?"top":"left"],g,g-h[j])}if(l){var x=p==="y"?"bottom":"right";b=Fv(b+h[p==="y"?"top":"left"],b,b-h[x])}var k=c.fn(me(me({},n),{},{[m]:g,[p]:b}));return me(me({},k),{},{data:{x:k.x-o,y:k.y-r,enabled:{[m]:i,[p]:l}}})})()}},H0=function(e){return e===void 0&&(e={}),{name:"flip",options:e,fn:n=>bn(function*(){var o,r,{placement:s,middlewareData:a,rects:i,initialPlacement:l,platform:c,elements:d}=n,u=oc(e,n),{mainAxis:h=!0,crossAxis:p=!0,fallbackPlacements:m,fallbackStrategy:g="bestFit",fallbackAxisSideDirection:b="none",flipAlignment:j=!0}=u,x=Gf(u,Vm);if((o=a.arrow)!=null&&o.alignmentOffset)return{};var k=Ua(s),$=$s(l),S=Ua(l)===l,y=yield c.isRTL==null?void 0:c.isRTL(d.floating),R=m||(S||!j?[Xi(l)]:function(Se){var we=Xi(Se);return[Dc(Se),we,Dc(we)]}(l)),N=b!=="none";!m&&N&&R.push(...function(Se,we,Ne,de){var oe=Al(Se),ce=function(an,Mn,Y){var I=["left","right"],ee=["right","left"];switch(an){case"top":case"bottom":return Y?Mn?ee:I:Mn?I:ee;case"left":case"right":return Mn?["top","bottom"]:["bottom","top"];default:return[]}}(Ua(Se),Ne==="start",de);return oe&&(ce=ce.map(an=>an+"-"+oe),we&&(ce=ce.concat(ce.map(Dc)))),ce}(l,j,b,y));var _=[l,...R],z=yield hh(n,x),W=[],X=((r=a.flip)==null?void 0:r.overflows)||[];if(h&&W.push(z[k]),p){var H=function(Se,we,Ne){Ne===void 0&&(Ne=!1);var de=Al(Se),oe=ph(Se),ce=fh(oe),an=oe==="x"?de===(Ne?"end":"start")?"right":"left":de==="start"?"bottom":"top";return we.reference[ce]>we.floating[ce]&&(an=Xi(an)),[an,Xi(an)]}(s,i,y);W.push(z[H[0]],z[H[1]])}if(X=[...X,{placement:s,overflows:W}],!W.every(Se=>Se<=0)){var ne,ye,te=(((ne=a.flip)==null?void 0:ne.index)||0)+1,ve=_[te];if(ve)return{data:{index:te,overflows:X},reset:{placement:ve}};var Re=(ye=X.filter(Se=>Se.overflows[0]<=0).sort((Se,we)=>Se.overflows[1]-we.overflows[1])[0])==null?void 0:ye.placement;if(!Re)switch(g){case"bestFit":var Ee,fe=(Ee=X.filter(Se=>{if(N){var we=$s(Se.placement);return we===$||we==="y"}return!0}).map(Se=>[Se.placement,Se.overflows.filter(we=>we>0).reduce((we,Ne)=>we+Ne,0)]).sort((Se,we)=>Se[1]-we[1])[0])==null?void 0:Ee[0];fe&&(Re=fe);break;case"initialPlacement":Re=l}if(s!==Re)return{reset:{placement:Re}}}return{}})()}};function Y0(e){var n,o,r={autoUpdate:!0},s=e,a=c=>me(me(me({},r),e||{}),c||{}),i=c=>{n&&o&&(s=a(c),((d,u,h)=>{var p=new Map,m=me({platform:J0},h),g=me(me({},m.platform),{},{_c:p});return _0(d,u,me(me({},m),{},{platform:g}))})(n,o,s).then(d=>{var u;Object.assign(o.style,{position:d.strategy,left:"".concat(d.x,"px"),top:"".concat(d.y,"px")}),!((u=s)===null||u===void 0)&&u.onComputed&&s.onComputed(d)}))},l=c=>{rr(c.subscribe(d=>{n===void 0?(n=d,i()):(Object.assign(n,d),i())}))};return[c=>{if("subscribe"in c)return l(c),{};n=c,i()},(c,d)=>{var u;o=c,s=a(d),setTimeout(()=>i(d),0),i(d);var h=()=>{u&&(u(),u=void 0)},p=function(){var{autoUpdate:m}=arguments.length>0&&arguments[0]!==void 0?arguments[0]:s||{};h(),m!==!1&&function(){return hp.apply(this,arguments)}().then(()=>B0(n,o,()=>i(s),m===!0?{}:m))};return u=p(),{update(m){i(m),u=p(m)},destroy(){h()}}},i]}function K0(e){var{loadOptions:n,filterText:o,items:r,multiple:s,value:a,itemId:i,groupBy:l,filterSelectedItems:c,itemFilter:d,convertStringItemsToObjects:u,filterGroupedItems:h,label:p}=e;if(r&&n)return r;if(!r)return[];r&&r.length>0&&typeof r[0]!="object"&&(r=u(r));var m=r.filter(g=>{var b=d(g[p],o,g);return b&&s&&a!=null&&a.length&&(b=!a.some(j=>!!c&&j[i]===g[i])),b});return l&&(m=h(m)),m}function Q0(e){return wh.apply(this,arguments)}function wh(){return(wh=bn(function*(e){var{dispatch:n,loadOptions:o,convertStringItemsToObjects:r,filterText:s}=e,a=yield o(s).catch(i=>{console.warn("svelte-select loadOptions error :>> ",i),n("error",{type:"loadOptions",details:i})});if(a&&!a.cancelled)return a?(a&&a.length>0&&typeof a[0]!="object"&&(a=r(a)),n("loaded",{items:a})):a=[],{filteredItems:a,loading:!1,focused:!0,listOpen:!0}})).apply(this,arguments)}fn(`
  svg.svelte-qbd276 {
      width: var(--chevron-icon-width, 20px);
      height: var(--chevron-icon-width, 20px);
      color: var(--chevron-icon-colour, currentColor);
  }
`);var G0=$a(`<svg width="100%" height="100%" viewBox="0 0 20 20" focusable="false" aria-hidden="true" class="svelte-qbd276"><path fill="currentColor" d="M4.516 7.548c0.436-0.446 1.043-0.481 1.576 0l3.908 3.747
          3.908-3.747c0.533-0.481 1.141-0.446 1.574 0 0.436 0.445 0.408 1.197 0
          1.615-0.406 0.418-4.695 4.502-4.695 4.502-0.217 0.223-0.502
          0.335-0.787 0.335s-0.57-0.112-0.789-0.335c0
          0-4.287-4.084-4.695-4.502s-0.436-1.17 0-1.615z"></path></svg>`);fn(`
    svg.svelte-whdbu1 {
        width: var(--clear-icon-width, 20px);
        height: var(--clear-icon-width, 20px);
        color: var(--clear-icon-color, currentColor);
    }
`);var X0=$a(`<svg width="100%" height="100%" viewBox="-2 -2 50 50" focusable="false" aria-hidden="true" role="presentation" class="svelte-whdbu1"><path fill="currentColor" d="M34.923,37.251L24,26.328L13.077,37.251L9.436,33.61l10.923-10.923L9.436,11.765l3.641-3.641L24,19.047L34.923,8.124
    l3.641,3.641L27.641,22.688L38.564,33.61L34.923,37.251z"></path></svg>`);function Lc(e){A(e,X0())}fn(`
    .loading.svelte-1p3nqvd {
        width: var(--spinner-width, 20px);
        height: var(--spinner-height, 20px);
        color: var(--spinner-color, var(--icons-color));
        animation: svelte-1p3nqvd-rotate 0.75s linear infinite;
        transform-origin: center center;
        transform: none;
    }

    .circle_path.svelte-1p3nqvd {
        stroke-dasharray: 90;
        stroke-linecap: round;
    }

    @keyframes svelte-1p3nqvd-rotate {
        100% {
            transform: rotate(360deg);
        }
    }
`);var Z0=$a('<svg class="loading svelte-1p3nqvd" viewBox="25 25 50 50"><circle class="circle_path svelte-1p3nqvd" cx="50" cy="50" r="20" fill="none" stroke="currentColor" stroke-width="5" stroke-miterlimit="10"></circle></svg>');fn(`
    .svelte-select.svelte-82qwg8 {
        /* deprecating camelCase custom props in favour of kebab-case for v5 */
        --borderRadius: var(--border-radius);
        --clearSelectColor: var(--clear-select-color);
        --clearSelectWidth: var(--clear-select-width);
        --disabledBackground: var(--disabled-background);
        --disabledBorderColor: var(--disabled-border-color);
        --disabledColor: var(--disabled-color);
        --disabledPlaceholderColor: var(--disabled-placeholder-color);
        --disabledPlaceholderOpacity: var(--disabled-placeholder-opacity);
        --errorBackground: var(--error-background);
        --errorBorder: var(--error-border);
        --groupItemPaddingLeft: var(--group-item-padding-left);
        --groupTitleColor: var(--group-title-color);
        --groupTitleFontSize: var(--group-title-font-size);
        --groupTitleFontWeight: var(--group-title-font-weight);
        --groupTitlePadding: var(--group-title-padding);
        --groupTitleTextTransform: var(--group-title-text-transform);
        --groupTitleBorderColor: var(--group-title-border-color);
        --groupTitleBorderWidth: var(--group-title-border-width);
        --groupTitleBorderStyle: var(--group-title-border-style);
        --indicatorColor: var(--chevron-color);
        --indicatorHeight: var(--chevron-height);
        --indicatorWidth: var(--chevron-width);
        --inputColor: var(--input-color);
        --inputLeft: var(--input-left);
        --inputLetterSpacing: var(--input-letter-spacing);
        --inputMargin: var(--input-margin);
        --inputPadding: var(--input-padding);
        --itemActiveBackground: var(--item-active-background);
        --itemColor: var(--item-color);
        --itemFirstBorderRadius: var(--item-first-border-radius);
        --itemHoverBG: var(--item-hover-bg);
        --itemHoverColor: var(--item-hover-color);
        --itemIsActiveBG: var(--item-is-active-bg);
        --itemIsActiveColor: var(--item-is-active-color);
        --itemIsNotSelectableColor: var(--item-is-not-selectable-color);
        --itemPadding: var(--item-padding);
        --listBackground: var(--list-background);
        --listBorder: var(--list-border);
        --listBorderRadius: var(--list-border-radius);
        --listEmptyColor: var(--list-empty-color);
        --listEmptyPadding: var(--list-empty-padding);
        --listEmptyTextAlign: var(--list-empty-text-align);
        --listMaxHeight: var(--list-max-height);
        --listPosition: var(--list-position);
        --listShadow: var(--list-shadow);
        --listZIndex: var(--list-z-index);
        --multiItemBG: var(--multi-item-bg);
        --multiItemBorderRadius: var(--multi-item-border-radius);
        --multiItemDisabledHoverBg: var(--multi-item-disabled-hover-bg);
        --multiItemDisabledHoverColor: var(--multi-item-disabled-hover-color);
        --multiItemHeight: var(--multi-item-height);
        --multiItemMargin: var(--multi-item-margin);
        --multiItemPadding: var(--multi-item-padding);
        --multiSelectInputMargin: var(--multi-select-input-margin);
        --multiSelectInputPadding: var(--multi-select-input-padding);
        --multiSelectPadding: var(--multi-select-padding);
        --placeholderColor: var(--placeholder-color);
        --placeholderOpacity: var(--placeholder-opacity);
        --selectedItemPadding: var(--selected-item-padding);
        --spinnerColor: var(--spinner-color);
        --spinnerHeight: var(--spinner-height);
        --spinnerWidth: var(--spinner-width);

        --internal-padding: 0 0 0 16px;

        border: var(--border, 1px solid #d8dbdf);
        border-radius: var(--border-radius, 6px);
        min-height: var(--height, 42px);
        position: relative;
        display: flex;
        align-items: stretch;
        padding: var(--padding, var(--internal-padding));
        background: var(--background, #fff);
        margin: var(--margin, 0);
        width: var(--width, 100%);
        font-size: var(--font-size, 16px);
        max-height: var(--max-height);
    }

    .svelte-82qwg8 {
        box-sizing: var(--box-sizing, border-box);
    }

    .svelte-select.svelte-82qwg8:hover {
        border: var(--border-hover, 1px solid #b2b8bf);
    }

    .value-container.svelte-82qwg8 {
        display: flex;
        flex: 1 1 0%;
        flex-wrap: wrap;
        align-items: center;
        gap: 5px 10px;
        padding: var(--value-container-padding, 5px 0);
        position: relative;
        overflow: var(--value-container-overflow, hidden);
        align-self: stretch;
    }

    .prepend.svelte-82qwg8,
    .indicators.svelte-82qwg8 {
        display: flex;
        flex-shrink: 0;
        align-items: center;
    }

    .indicators.svelte-82qwg8 {
        position: var(--indicators-position);
        top: var(--indicators-top);
        right: var(--indicators-right);
        bottom: var(--indicators-bottom);
    }

    input.svelte-82qwg8 {
        position: absolute;
        cursor: default;
        border: none;
        color: var(--input-color, var(--item-color));
        padding: var(--input-padding, 0);
        letter-spacing: var(--input-letter-spacing, inherit);
        margin: var(--input-margin, 0);
        min-width: 10px;
        top: 0;
        right: 0;
        bottom: 0;
        left: 0;
        background: transparent;
        font-size: var(--font-size, 16px);
    }

    .svelte-82qwg8:not(.multi) > .value-container:where(.svelte-82qwg8) > input:where(.svelte-82qwg8) {
        width: 100%;
        height: 100%;
    }

    input.svelte-82qwg8::placeholder {
        color: var(--placeholder-color, #78848f);
        opacity: var(--placeholder-opacity, 1);
    }

    input.svelte-82qwg8:focus {
        outline: none;
    }

    .svelte-select.focused.svelte-82qwg8 {
        border: var(--border-focused, 1px solid #006fe8);
        border-radius: var(--border-radius-focused, var(--border-radius, 6px));
    }

    .disabled.svelte-82qwg8 {
        background: var(--disabled-background, #ebedef);
        border-color: var(--disabled-border-color, #ebedef);
        color: var(--disabled-color, #c1c6cc);
    }

    .disabled.svelte-82qwg8 input:where(.svelte-82qwg8)::placeholder {
        color: var(--disabled-placeholder-color, #c1c6cc);
        opacity: var(--disabled-placeholder-opacity, 1);
    }

    .selected-item.svelte-82qwg8 {
        position: relative;
        overflow: var(--selected-item-overflow, hidden);
        padding: var(--selected-item-padding, 0 20px 0 0);
        text-overflow: ellipsis;
        white-space: nowrap;
        color: var(--selected-item-color, inherit);
        font-size: var(--font-size, 16px);
    }

    .multi.svelte-82qwg8 .selected-item:where(.svelte-82qwg8) {
        position: absolute;
        line-height: var(--height, 42px);
        height: var(--height, 42px);
    }

    .selected-item.svelte-82qwg8:focus {
        outline: none;
    }

    .hide-selected-item.svelte-82qwg8 {
        opacity: 0;
    }

    .icon.svelte-82qwg8 {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .clear-select.svelte-82qwg8 {
        all: unset;
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--clear-select-width, 40px);
        height: var(--clear-select-height, 100%);
        color: var(--clear-select-color, var(--icons-color));
        margin: var(--clear-select-margin, 0);
        pointer-events: all;
        flex-shrink: 0;
    }

    .clear-select.svelte-82qwg8:focus {
        outline: var(--clear-select-focus-outline, 1px solid #006fe8);
    }

    .loading.svelte-82qwg8 {
        width: var(--loading-width, 40px);
        height: var(--loading-height);
        color: var(--loading-color, var(--icons-color));
        margin: var(--loading--margin, 0);
        flex-shrink: 0;
    }

    .chevron.svelte-82qwg8 {
        width: var(--chevron-width, 40px);
        height: var(--chevron-height, 40px);
        background: var(--chevron-background, transparent);
        pointer-events: var(--chevron-pointer-events, none);
        color: var(--chevron-color, var(--icons-color));
        border: var(--chevron-border, 0 0 0 1px solid #d8dbdf);
        flex-shrink: 0;
    }

    .multi.svelte-82qwg8 {
        padding: var(--multi-select-padding, var(--internal-padding));
    }

    .multi.svelte-82qwg8 input:where(.svelte-82qwg8) {
        padding: var(--multi-select-input-padding, 0);
        position: relative;
        margin: var(--multi-select-input-margin, 5px 0);
        flex: 1 1 40px;
    }

    .svelte-select.error.svelte-82qwg8 {
        border: var(--error-border, 1px solid #ff2d55);
        background: var(--error-background, #fff);
    }

    .a11y-text.svelte-82qwg8 {
        z-index: 9999;
        border: 0px;
        clip: rect(1px, 1px, 1px, 1px);
        height: 1px;
        width: 1px;
        position: absolute;
        overflow: hidden;
        padding: 0px;
        white-space: nowrap;
    }

    .multi-item.svelte-82qwg8 {
        background: var(--multi-item-bg, #ebedef);
        margin: var(--multi-item-margin, 0);
        outline: var(--multi-item-outline, 1px solid #ddd);
        border-radius: var(--multi-item-border-radius, 4px);
        height: var(--multi-item-height, 25px);
        line-height: var(--multi-item-height, 25px);
        display: flex;
        cursor: default;
        padding: var(--multi-item-padding, 0 5px);
        overflow: hidden;
        gap: var(--multi-item-gap, 4px);
        outline-offset: -1px;
        max-width: var(--multi-max-width, none);
        color: var(--multi-item-color, var(--item-color));
    }

    .multi-item.disabled.svelte-82qwg8:hover {
        background: var(--multi-item-disabled-hover-bg, #ebedef);
        color: var(--multi-item-disabled-hover-color, #c1c6cc);
    }

    .multi-item-text.svelte-82qwg8 {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .multi-item-clear.svelte-82qwg8 {
        display: flex;
        align-items: center;
        justify-content: center;
        --clear-icon-color: var(--multi-item-clear-icon-color, #000);
    }

    .multi-item.active.svelte-82qwg8 {
        outline: var(--multi-item-active-outline, 1px solid #006fe8);
    }

    .svelte-select-list.svelte-82qwg8 {
        box-shadow: var(--list-shadow, 0 2px 3px 0 rgba(44, 62, 80, 0.24));
        border-radius: var(--list-border-radius, 4px);
        max-height: var(--list-max-height, 252px);
        overflow-y: auto;
        background: var(--list-background, #fff);
        position: var(--list-position, absolute);
        z-index: var(--list-z-index, 2);
        border: var(--list-border);
    }

    .prefloat.svelte-82qwg8 {
        opacity: 0;
        pointer-events: none;
    }

    .list-group-title.svelte-82qwg8 {
        color: var(--group-title-color, #8f8f8f);
        cursor: default;
        font-size: var(--group-title-font-size, 16px);
        font-weight: var(--group-title-font-weight, 600);
        height: var(--height, 42px);
        line-height: var(--height, 42px);
        padding: var(--group-title-padding, 0 20px);
        text-overflow: ellipsis;
        overflow-x: hidden;
        white-space: nowrap;
        text-transform: var(--group-title-text-transform, uppercase);
        border-width: var(--group-title-border-width, medium);
        border-style: var(--group-title-border-style, none);
        border-color: var(--group-title-border-color, color);
    }

    .empty.svelte-82qwg8 {
        text-align: var(--list-empty-text-align, center);
        padding: var(--list-empty-padding, 20px 0);
        color: var(--list-empty-color, #78848f);
    }

    .item.svelte-82qwg8 {
        cursor: default;
        height: var(--item-height, var(--height, 42px));
        line-height: var(--item-line-height, var(--height, 42px));
        padding: var(--item-padding, 0 20px);
        color: var(--item-color, inherit);
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
        transition: var(--item-transition, all 0.2s);
        align-items: center;
        width: 100%;
    }

    .item.group-item.svelte-82qwg8 {
        padding-left: var(--group-item-padding-left, 40px);
    }

    .item.svelte-82qwg8:active {
        background: var(--item-active-background, #b9daff);
    }

    .item.active.svelte-82qwg8 {
        background: var(--item-is-active-bg, #007aff);
        color: var(--item-is-active-color, #fff);
    }

    .item.first.svelte-82qwg8 {
        border-radius: var(--item-first-border-radius, 4px 4px 0 0);
    }

    .item.hover.svelte-82qwg8:not(.active) {
        background: var(--item-hover-bg, #e7f2ff);
        color: var(--item-hover-color, inherit);
    }

    .item.not-selectable.svelte-82qwg8,
    .item.hover.item.not-selectable.svelte-82qwg8,
    .item.active.item.not-selectable.svelte-82qwg8,
    .item.not-selectable.svelte-82qwg8:active {
        color: var(--item-is-not-selectable-color, #999);
        background: transparent;
    }

    .required.svelte-82qwg8 {
        opacity: 0;
        z-index: -1;
        position: absolute;
        top: 0;
        left: 0;
        bottom: 0;
        right: 0;
    }
`);var ej=L('<div class="list-item svelte-82qwg8" tabindex="-1" role="none"><div><!></div></div>'),nj=L('<div class="empty svelte-82qwg8">No options</div>'),tj=L('<div role="none"><!> <!> <!></div>'),oj=L('<span id="aria-selection" class="svelte-82qwg8"> </span> <span id="aria-context" class="svelte-82qwg8"> </span>',1),rj=L('<div class="multi-item-clear svelte-82qwg8"><!></div>'),aj=L('<div role="none"><span class="multi-item-text svelte-82qwg8"><!></span> <!></div>'),sj=L("<div><!></div>"),ij=L('<div class="icon loading svelte-82qwg8" aria-hidden="true"><!></div>'),lj=L('<button type="button" class="icon clear-select svelte-82qwg8"><!></button>'),cj=L('<div class="icon chevron svelte-82qwg8" aria-hidden="true"><!></div>'),uj=L('<input type="hidden" class="svelte-82qwg8">'),dj=L('<select class="required svelte-82qwg8" required tabindex="-1" aria-hidden="true"></select>'),vj=L('<div role="none"><!> <span aria-live="polite" aria-atomic="false" aria-relevant="additions text" class="a11y-text svelte-82qwg8"><!></span> <div class="prepend svelte-82qwg8"><!></div> <div class="value-container svelte-82qwg8"><!> <input></div> <div class="indicators svelte-82qwg8"><!> <!> <!></div> <!> <!></div>');function Ta(e,n){var o=function(U){var Z={};for(var he in U.children&&(Z.default=!0),U.$$slots)Z[he]=!0;return Z}(n);en(n,!1);var r,s=E(),a=E(),i=E(),l=E(),c=E(),d=E(),u=E(),h=E(),p=E(),m=bb(),g=f(n,"justValue",12,null),b=f(n,"filter",8,K0),j=f(n,"getItems",8,Q0),x=f(n,"id",8,null),k=f(n,"name",8,null),$=f(n,"container",12,void 0),S=f(n,"input",12,void 0),y=f(n,"multiple",8,!1),R=f(n,"multiFullItemClearable",8,!1),N=f(n,"disabled",8,!1),_=f(n,"focused",12,!1),z=f(n,"value",12,null),W=f(n,"filterText",12,""),X=f(n,"placeholder",8,"Please select"),H=f(n,"placeholderAlwaysShow",8,!1),ne=f(n,"items",12,null),ye=f(n,"label",8,"label"),te=f(n,"itemFilter",8,(U,Z,he)=>"".concat(U).toLowerCase().includes(Z.toLowerCase())),ve=f(n,"groupBy",8,void 0),Re=f(n,"groupFilter",8,U=>U),Ee=f(n,"groupHeaderSelectable",8,!1),fe=f(n,"itemId",8,"value"),Se=f(n,"loadOptions",8,void 0),we=f(n,"containerStyles",8,""),Ne=f(n,"hasError",8,!1),de=f(n,"filterSelectedItems",8,!0),oe=f(n,"required",8,!1),ce=f(n,"closeListOnChange",8,!0),an=f(n,"clearFilterTextOnBlur",8,!0),Mn=f(n,"createGroupHeaderItem",8,(U,Z)=>({value:U,[ye()]:U})),Y=()=>t(u),I=f(n,"searchable",8,!0),ee=f(n,"inputStyles",8,""),P=f(n,"clearable",8,!0),pe=f(n,"loading",12,!1),K=f(n,"listOpen",12,!1),Me=f(n,"debounce",8,function(U){var Z=arguments.length>1&&arguments[1]!==void 0?arguments[1]:1;clearTimeout(r),r=setTimeout(U,Z)}),V=f(n,"debounceWait",8,300),T=f(n,"hideEmptyState",8,!1),xn=f(n,"inputAttributes",24,()=>({})),Qe=f(n,"listAutoWidth",8,!0),cn=f(n,"showChevron",8,!1),sn=f(n,"listOffset",8,5),je=f(n,"hoverItemIndex",12,0),Xe=f(n,"floatingConfig",24,()=>({})),$n=f(n,"class",8,""),De=E(),Nn=E(),ge=E(),qe=E(),Ae=E();function Ue(U){return U.map((Z,he)=>({index:he,value:Z,label:"".concat(Z)}))}function Fn(U){var Z=[],he={};U.forEach(it=>{var Xn=ve()(it);Z.includes(Xn)||(Z.push(Xn),he[Xn]=[],Xn&&he[Xn].push(Object.assign(Mn()(Xn,it),{id:Xn,groupHeader:!0,selectable:Ee()}))),he[Xn].push(Object.assign({groupItem:!!Xn},it))});var An=[];return Re()(Z).forEach(it=>{he[it]&&An.push(...he[it])}),An}function gn(){var U=arguments.length>0&&arguments[0]!==void 0?arguments[0]:0,Z=arguments.length>1?arguments[1]:void 0;je(U<0?0:U),!Z&&ve()&&t(u)[je()]&&!t(u)[je()].selectable&&Wn(1)}function Ve(){var U=!0;if(z()){var Z=[],he=[];z().forEach(An=>{Z.includes(An[fe()])?U=!1:(Z.push(An[fe()]),he.push(An))}),U||z(he)}return U}function D(U){var Z=U?U[fe()]:z()[fe()];return ne().find(he=>he[fe()]===Z)}function Ke(U){return Ge.apply(this,arguments)}function Ge(){return(Ge=bn(function*(U){var Z=z()[U];z().length===1?z(void 0):z(z().filter(he=>he!==Z)),m("clear",Z)})).apply(this,arguments)}function Ct(U){if(_())switch(U.stopPropagation(),U.key){case"Escape":U.preventDefault(),_n();break;case"Enter":if(U.preventDefault(),K()){if(t(u).length===0)break;var Z=t(u)[je()];if(z()&&!y()&&z()[fe()]===Z[fe()]){_n();break}on(t(u)[je()])}break;case"ArrowDown":U.preventDefault(),K()?Wn(1):(K(!0),v(De,void 0));break;case"ArrowUp":U.preventDefault(),K()?Wn(-1):(K(!0),v(De,void 0));break;case"Tab":if(K()&&_()){if(t(u).length===0||z()&&z()[fe()]===t(u)[je()][fe()])return _n();U.preventDefault(),on(t(u)[je()]),_n()}break;case"Backspace":if(!y()||W().length>0)return;if(y()&&z()&&z().length>0){if(Ke(t(De)!==void 0?t(De):z().length-1),t(De)===0||t(De)===void 0)break;v(De,z().length>t(De)?t(De)-1:void 0)}break;case"ArrowLeft":if(!z()||!y()||W().length>0)return;t(De)===void 0?v(De,z().length-1):z().length>t(De)&&t(De)!==0&&v(De,t(De)-1);break;case"ArrowRight":if(!z()||!y()||W().length>0||t(De)===void 0)return;t(De)===z().length-1?v(De,void 0):t(De)<z().length-1&&v(De,t(De)+1)}}function _e(U){var Z,he;_()&&S()===((Z=document)===null||Z===void 0?void 0:Z.activeElement)||(U&&m("focus",U),(he=S())===null||he===void 0||he.focus(),_(!0))}function Rn(U){return Un.apply(this,arguments)}function Un(){return(Un=bn(function*(U){var Z;st||(K()||_())&&(m("blur",U),_n(),_(!1),v(De,void 0),(Z=S())===null||Z===void 0||Z.blur())})).apply(this,arguments)}function co(){if(!N())return W().length>0?K(!0):void K(!K())}function ut(){m("clear",z()),z(void 0),_n(),_e()}function _n(){an()&&W(""),K(!1)}jb(bn(function*(){v(Nn,z()),v(ge,W()),v(qe,y())})),Po(()=>{K()&&_(!0),_()&&S()&&S().focus()});var Lt=f(n,"ariaValues",8,U=>"Option ".concat(U,", selected.")),Qn=f(n,"ariaListOpen",8,(U,Z)=>"You are currently focused on option ".concat(U,". There are ").concat(Z," results available.")),ft=f(n,"ariaFocused",8,()=>"Select is focused, type to refine list, press down to open the menu."),rt,Ln=E(null);function yn(){clearTimeout(rt),rt=setTimeout(()=>{st=!1},100)}rr(()=>{var U;(U=t(Ln))===null||U===void 0||U.remove()});var st=!1;function on(U){U&&U.selectable!==!1&&function(Z){if(Z){W("");var he=Object.assign({},Z);if(he.groupHeader&&!he.selectable)return;z(y()?z()?z().concat([he]):[he]:z(he)),setTimeout(()=>{ce()&&_n(),v(De,void 0),m("change",z()),m("select",Z)})}}(U)}function Jn(U){st||je(U)}function Wn(U){if(t(u).filter(he=>!Object.hasOwn(he,"selectable")||he.selectable===!0).length===0)return je(0);U>0&&je()===t(u).length-1?je(0):U<0&&je()===0?je(t(u).length-1):je(je()+U);var Z=t(u)[je()];Z&&Z.selectable===!1&&(U!==1&&U!==-1||Wn(U))}function Et(U,Z,he){if(!y())return Z&&Z[he]===U[he]}var Kt=vo,bt=vo;function vo(U){return{update(Z){Z.scroll&&(yn(),U.scrollIntoView({behavior:"auto",block:"nearest"}))}}}var oo=E({strategy:"absolute",placement:"bottom-start",middleware:[W0(sn()),H0(),V0()],autoUpdate:!1}),[uo,jo,fo]=Y0(t(oo)),Pt=E(!0);F(()=>(O(ne()),O(z())),()=>{ne(),z()&&function(){if(typeof z()=="string"){var U=(ne()||[]).find(Z=>Z[fe()]===z());z(U||{[fe()]:z(),label:z()})}else y()&&Array.isArray(z())&&z().length>0&&z(z().map(Z=>typeof Z=="string"?{value:Z,label:Z}:Z))}()}),F(()=>(O(xn()),O(I())),()=>{!xn()&&I()||(v(Ae,Object.assign({autocapitalize:"none",autocomplete:"off",autocorrect:"off",spellcheck:!1,tabindex:0,type:"text","aria-autocomplete":"list"},xn())),x()&&Ko(Ae,t(Ae).id=x()),I()||Ko(Ae,t(Ae).readonly=!0))}),F(()=>O(y()),()=>{y()&&z()&&(Array.isArray(z())?z([...z()]):z([z()]))}),F(()=>(t(qe),O(y())),()=>{t(qe)&&!y()&&z()&&z(null)}),F(()=>(O(y()),O(z())),()=>{y()&&z()&&z().length>1&&Ve()}),F(()=>O(z()),()=>{z()&&(y()?JSON.stringify(z())!==JSON.stringify(t(Nn))&&Ve()&&m("input",z()):t(Nn)&&JSON.stringify(z()[fe()])===JSON.stringify(t(Nn)[fe()])||m("input",z()))}),F(()=>(O(z()),O(y()),t(Nn)),()=>{!z()&&y()&&t(Nn)&&m("input",z())}),F(()=>(O(_()),O(S())),()=>{!_()&&S()&&_n()}),F(()=>(O(W()),t(ge)),()=>{W()!==t(ge)&&(Se()||W().length!==0)&&(Se()?Me()(bn(function*(){pe(!0);var U=yield j()({dispatch:m,loadOptions:Se(),convertStringItemsToObjects:Ue,filterText:W()});U?(pe(U.loading),K(K()?U.listOpen:W().length>0),_(K()&&U.focused),ne(ve()?Fn(U.filteredItems):U.filteredItems)):(pe(!1),_(!0),K(!0))}),V()):(K(!0),y()&&v(De,void 0)))}),F(()=>(O(b()),O(Se()),O(W()),O(ne()),O(y()),O(z()),O(fe()),O(ve()),O(ye()),O(de()),O(te())),()=>{v(u,b()({loadOptions:Se(),filterText:W(),items:ne(),multiple:y(),value:z(),itemId:fe(),groupBy:ve(),label:ye(),filterSelectedItems:de(),itemFilter:te(),convertStringItemsToObjects:Ue,filterGroupedItems:Fn}))}),F(()=>(O(y()),O(K()),O(z()),t(u)),()=>{!y()&&K()&&z()&&t(u)&&gn(t(u).findIndex(U=>U[fe()]===z()[fe()]),!0)}),F(()=>(O(K()),O(y())),()=>{K()&&y()&&je(0)}),F(()=>O(W()),()=>{W()&&je(0)}),F(()=>O(je()),()=>{var U;U=je(),m("hoverItem",U)}),F(()=>(O(y()),O(z())),()=>{v(s,y()?z()&&z().length>0:z())}),F(()=>(t(s),O(W())),()=>{v(a,t(s)&&W().length>0)}),F(()=>(t(s),O(P()),O(N()),O(pe())),()=>{v(i,t(s)&&P()&&!N()&&!pe())}),F(()=>(O(H()),O(y()),O(X()),O(z())),()=>{var U;v(l,H()&&y()||y()&&((U=z())===null||U===void 0?void 0:U.length)===0?X():z()?"":X())}),F(()=>(O(z()),O(y())),()=>{var U,Z;v(c,z()?(U=y(),Z=void 0,Z=U&&z().length>0?z().map(he=>he[ye()]).join(", "):z()[ye()],Lt()(Z)):"")}),F(()=>(t(u),O(je()),O(_()),O(K())),()=>{v(d,function(){if(!t(u)||t(u).length===0)return"";var U=t(u)[je()];if(K()&&U){var Z=t(u)?t(u).length:0;return Qn()(U[ye()],Z)}return ft()()}((t(u),je(),_(),K())))}),F(()=>O(ne()),()=>{(function(U){U&&U.length!==0&&!U.some(Z=>typeof Z!="object")&&z()&&(y()?!z().some(Z=>!Z||!Z[fe()]):z()[fe()])&&(Array.isArray(z())?z(z().map(Z=>D(Z)||Z)):z(D()||z()))})(ne())}),F(()=>(O(y()),O(z()),O(fe())),()=>{g((y(),z(),fe(),y()?z()?z().map(U=>U[fe()]):null:z()?z()[fe()]:z()))}),F(()=>(O(y()),t(Nn),O(z())),()=>{y()||!t(Nn)||z()||m("input",z())}),F(()=>(O(K()),t(u),O(y()),O(z())),()=>{K()&&t(u)&&!y()&&!z()&&gn()}),F(()=>t(u),()=>{(function(U){K()&&m("filter",U)})(t(u))}),F(()=>(O($()),O(Xe()),t(oo)),()=>{$()&&Xe()&&fo(Object.assign(t(oo),Xe()))}),F(()=>t(Ln),()=>{v(h,!!t(Ln))}),F(()=>(t(Ln),O(K())),()=>{(function(U,Z){if(!U||!Z)return v(Pt,!0);setTimeout(()=>{v(Pt,!1)},0)})(t(Ln),K())}),F(()=>(O(K()),O($()),t(Ln)),()=>{K()&&$()&&t(Ln)&&function(){var{width:U}=$().getBoundingClientRect();Ko(Ln,t(Ln).style.width=Qe()?U+"px":"auto")}()}),F(()=>O(je()),()=>{v(p,je())}),F(()=>(O(S()),O(K()),O(_())),()=>{S()&&K()&&!_()&&_e()}),F(()=>(O($()),O(Xe())),()=>{var U;$()&&((U=Xe())===null||U===void 0?void 0:U.autoUpdate)===void 0&&Ko(oo,t(oo).autoUpdate=!0)}),mt(),jn();var It,Zt=vj();ie("click",Qr,function(U){var Z;K()||_()||!$()||$().contains(U.target)||(Z=t(Ln))!==null&&Z!==void 0&&Z.contains(U.target)||Rn()}),ie("keydown",Qr,Ct);var le=M(Zt),Gn=U=>{var Z,he=tj(),An=M(he),it=mn=>{var _t=to();io(rn(_t),n,"list-prepend",{},null),A(mn,_t)};Q(An,mn=>{o["list-prepend"]&&mn(it)});var Xn=q(An,2),jt=mn=>{var _t=to();io(rn(_t),n,"list",{get filteredItems(){return t(u)}},null),A(mn,_t)},Ut=(mn,_t)=>{var vn=$e=>{var at=to();lo(rn(at),1,()=>t(u),ho,(zt,lt,ro)=>{var _o,C=ej(),G=M(C);io(M(G),n,"item",{get item(){return t(lt)},index:ro},re=>{var ue=zo();be(()=>{var xe;return ln(ue,(xe=t(lt))===null||xe===void 0?void 0:xe[ye()])}),A(re,ue)}),Uo(G,(re,ue)=>Kt==null?void 0:Kt(re),()=>({scroll:Et(t(lt),z(),fe()),listDom:t(h)})),Uo(G,(re,ue)=>bt==null?void 0:bt(re),()=>({scroll:t(p)===ro,listDom:t(h)})),be(re=>_o=kn(G,1,"item svelte-82qwg8",null,_o,re),[()=>{var re,ue;return{"list-group-title":t(lt).groupHeader,active:Et(t(lt),z(),fe()),first:(ue=ro,ue===0),hover:je()===ro,"group-item":t(lt).groupItem,"not-selectable":((re=t(lt))===null||re===void 0?void 0:re.selectable)===!1}}],ae),ie("mouseover",C,()=>Jn(ro)),ie("focus",C,()=>Jn(ro)),ie("click",C,Jr(()=>function(re){var{item:ue,i:xe}=re;if((ue==null?void 0:ue.selectable)!==!1)return z()&&!y()&&z()[fe()]===ue[fe()]?_n():void(function(Te){return Te.groupHeader&&Te.selectable||Te.selectable||!Te.hasOwnProperty("selectable")}(ue)&&(je(xe),on(ue)))}({item:t(lt),i:ro}))),ie("keydown",C,ua(Jr(function(re){ai.call(this,n,re)}))),A(zt,C)}),A($e,at)},xo=($e,at)=>{var zt=lt=>{var ro=to();io(rn(ro),n,"empty",{},_o=>{A(_o,nj())}),A(lt,ro)};Q($e,lt=>{T()||lt(zt)},at)};Q(mn,$e=>{t(u).length>0?$e(vn):$e(xo,!1)},_t)};Q(Xn,mn=>{o.list?mn(jt):mn(Ut,!1)});var qt=q(Xn,2),Bt=mn=>{var _t=to();io(rn(_t),n,"list-append",{},null),A(mn,_t)};Q(qt,mn=>{o["list-append"]&&mn(Bt)}),Uo(he,mn=>jo==null?void 0:jo(mn)),Yt(he,mn=>v(Ln,mn),()=>t(Ln)),$o(()=>ie("scroll",he,yn)),$o(()=>ie("pointerup",he,ua(Jr(function(mn){ai.call(this,n,mn)})))),$o(()=>ie("mousedown",he,ua(Jr(function(mn){ai.call(this,n,mn)})))),be(mn=>Z=kn(he,1,"svelte-select-list svelte-82qwg8",null,Z,mn),[()=>({prefloat:t(Pt)})],ae),A(U,he)};Q(le,U=>{K()&&U(Gn)});var B=q(le,2),ke=M(B),He=U=>{var Z=oj(),he=rn(Z),An=M(he),it=M(q(he,2));be(()=>{ln(An,t(c)),ln(it,t(d))}),A(U,Z)};Q(ke,U=>{_()&&U(He)});var zn=q(B,2);io(M(zn),n,"prepend",{},null);var We=q(zn,2),Tn=M(We),dt=U=>{var Z=to(),he=rn(Z),An=Xn=>{var jt=to();lo(rn(jt),1,z,ho,(Ut,qt,Bt)=>{var mn,_t=aj(),vn=M(_t);io(M(vn),n,"selection",{get selection(){return t(qt)},index:Bt},at=>{var zt=zo();be(()=>ln(zt,t(qt)[ye()])),A(at,zt)});var xo=q(vn,2),$e=at=>{var zt=rj();io(M(zt),n,"multi-clear-icon",{},lt=>{Lc(lt)}),ie("pointerup",zt,ua(Jr(()=>Ke(Bt)))),A(at,zt)};Q(xo,at=>{N()||R()||!Lc||at($e)}),be(at=>mn=kn(_t,1,"multi-item svelte-82qwg8",null,mn,at),[()=>({active:t(De)===Bt,disabled:N()})],ae),ie("click",_t,ua(()=>R()?Ke(Bt):{})),ie("keydown",_t,ua(Jr(function(at){ai.call(this,n,at)}))),A(Ut,_t)}),A(Xn,jt)},it=Xn=>{var jt,Ut=sj();io(M(Ut),n,"selection",{get selection(){return z()}},qt=>{var Bt=zo();be(()=>ln(Bt,z()[ye()])),A(qt,Bt)}),be(qt=>jt=kn(Ut,1,"selected-item svelte-82qwg8",null,jt,qt),[()=>({"hide-selected-item":t(a)})],ae),A(Xn,Ut)};Q(he,Xn=>{y()?Xn(An):Xn(it,!1)}),A(U,Z)};Q(Tn,U=>{t(s)&&U(dt)});var On,pt=q(Tn,2);Yt(pt,U=>S(U),()=>S());var un=q(We,2),En=M(un),tn=U=>{var Z=ij();io(M(Z),n,"loading-icon",{},he=>{(function(An){A(An,Z0())})(he)}),A(U,Z)};Q(En,U=>{pe()&&U(tn)});var pn=q(En,2),St=U=>{var Z=lj();io(M(Z),n,"clear-icon",{},he=>{Lc(he)}),ie("click",Z,ut),A(U,Z)};Q(pn,U=>{t(i)&&U(St)});var At=q(pn,2),Dn=U=>{var Z=cj();io(M(Z),n,"chevron-icon",{get listOpen(){return K()}},he=>{(function(An){A(An,G0())})(he)}),A(U,Z)};Q(At,U=>{cn()&&U(Dn)});var Pn=q(un,2);io(Pn,n,"input-hidden",{get value(){return z()}},U=>{var Z=uj();be(he=>{ht(Z,"name",k()),La(Z,he)},[()=>z()?JSON.stringify(z()):null],ae),A(U,Z)});var qn=q(Pn,2),Jt=U=>{var Z=to();io(rn(Z),n,"required",{get value(){return z()}},he=>{A(he,dj())}),A(U,Z)};return Q(qn,U=>{!oe()||z()&&z().length!==0||U(Jt)}),$o(()=>ie("pointerup",Zt,ua(co))),Yt(Zt,U=>$(U),()=>$()),Uo(Zt,U=>uo==null?void 0:uo(U)),be(U=>{var Z;It=kn(Zt,1,"svelte-select ".concat((Z=$n())!==null&&Z!==void 0?Z:""),"svelte-82qwg8",It,U),fr(Zt,we()),On=al(pt,On,me(me({readOnly:!I()},t(Ae)),{},{placeholder:t(l),style:ee(),disabled:N()}),"svelte-82qwg8")},[()=>({multi:y(),disabled:N(),focused:_(),"list-open":K(),"show-chevron":cn(),error:Ne()})],ae),ie("keydown",pt,Ct),ie("blur",pt,Rn),ie("focus",pt,_e),kl(pt,W),A(e,Zt),hn(n,"getFilteredItems",Y),hn(n,"handleClear",ut),nn({getFilteredItems:Y,handleClear:ut})}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
table.jse-transform-wizard.svelte-qbze6z {
  border-collapse: collapse;
  border-spacing: 0;
  width: 100%;
}
table.jse-transform-wizard.svelte-qbze6z input:where(.svelte-qbze6z) {
  font-family: inherit;
  font-size: inherit;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) th:where(.svelte-qbze6z) {
  font-weight: normal;
  text-align: left;
  width: 60px;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) {
  width: 100%;
  display: flex;
  flex-direction: row;
  margin-bottom: calc(0.5 * var(--jse-padding, 10px));
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select .multi-item {
  align-items: center;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select .value-container {
  gap: 0 !important;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select.jse-filter-path {
  flex: 4;
  margin-right: calc(0.5 * var(--jse-padding, 10px));
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select.jse-filter-relation {
  flex: 1.5;
  margin-right: calc(0.5 * var(--jse-padding, 10px));
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select.jse-sort-path {
  flex: 3;
  margin-right: calc(0.5 * var(--jse-padding, 10px));
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select.jse-sort-direction {
  flex: 1;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select.jse-projection-paths {
  flex: 1;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .svelte-select input {
  box-sizing: border-box;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .jse-filter-value:where(.svelte-qbze6z) {
  flex: 4;
  padding: 4px 8px;
  border: var(--jse-input-border, 1px solid #d8dbdf);
  border-radius: var(--jse-input-radius, 3px);
  outline: none;
  background: var(--jse-input-background, var(--jse-background-color, #fff));
  color: inherit;
}
table.jse-transform-wizard.svelte-qbze6z tr:where(.svelte-qbze6z) td:where(.svelte-qbze6z) .jse-horizontal:where(.svelte-qbze6z) .jse-filter-value:where(.svelte-qbze6z):focus {
  border: var(--jse-input-border-focus, 1px solid var(--jse-input-border-focus, var(--jse-theme-color, #3883fa)));
}`);var fj=L('<table class="jse-transform-wizard svelte-qbze6z"><tbody><tr class="svelte-qbze6z"><th class="svelte-qbze6z">Filter</th><td class="svelte-qbze6z"><div class="jse-horizontal svelte-qbze6z"><!> <!> <input class="jse-filter-value svelte-qbze6z"></div></td></tr><tr class="svelte-qbze6z"><th class="svelte-qbze6z">Sort</th><td class="svelte-qbze6z"><div class="jse-horizontal svelte-qbze6z"><!> <!></div></td></tr><tr class="svelte-qbze6z"><th class="svelte-qbze6z">Pick</th><td class="svelte-qbze6z"><div class="jse-horizontal svelte-qbze6z"><!></div></td></tr></tbody></table>');function pj(e,n){var o,r,s,a,i;en(n,!1);var l=E(void 0,!0),c=E(void 0,!0),d=E(void 0,!0),u=E(void 0,!0),h=E(void 0,!0),p=E(void 0,!0),m=So("jsoneditor:TransformWizard"),g=f(n,"json",9),b=f(n,"queryOptions",29,()=>({})),j=f(n,"onChange",9),x=["==","!=","<","<=",">",">="].map(de=>({value:de,label:de})),k=[{value:"asc",label:"ascending"},{value:"desc",label:"descending"}],$=E((o=b())!==null&&o!==void 0&&(o=o.filter)!==null&&o!==void 0&&o.path?fa(b().filter.path):void 0,!0),S=E((r=x.find(de=>{var oe;return de.value===((oe=b().filter)===null||oe===void 0?void 0:oe.relation)}))!==null&&r!==void 0?r:x[0],!0),y=E(((s=b())===null||s===void 0||(s=s.filter)===null||s===void 0?void 0:s.value)||"",!0),R=E((a=b())!==null&&a!==void 0&&(a=a.sort)!==null&&a!==void 0&&a.path?fa(b().sort.path):void 0,!0),N=E((i=k.find(de=>{var oe;return de.value===((oe=b().sort)===null||oe===void 0?void 0:oe.direction)}))!==null&&i!==void 0?i:k[0],!0);F(()=>O(g()),()=>{v(l,Array.isArray(g()))}),F(()=>(t(l),O(g())),()=>{v(c,t(l)?uu(g()):[])}),F(()=>(t(l),O(g())),()=>{v(d,t(l)?uu(g(),!0):[])}),F(()=>(t(c),fa),()=>{v(u,t(c).map(fa))}),F(()=>(t(d),fa),()=>{v(h,t(d)?t(d).map(fa):[])}),F(()=>(O(b()),t(h),et),()=>{var de;v(p,(de=b())!==null&&de!==void 0&&(de=de.projection)!==null&&de!==void 0&&de.paths&&t(h)?b().projection.paths.map(oe=>t(h).find(ce=>et(ce.value,oe))).filter(oe=>!!oe):void 0)}),F(()=>t($),()=>{var de,oe,ce;oe=(de=t($))===null||de===void 0?void 0:de.value,et((ce=b())===null||ce===void 0||(ce=ce.filter)===null||ce===void 0?void 0:ce.path,oe)||(m("changeFilterPath",oe),b(Lr(b(),["filter","path"],oe,!0)),j()(b()))}),F(()=>t(S),()=>{var de,oe,ce;oe=(de=t(S))===null||de===void 0?void 0:de.value,et((ce=b())===null||ce===void 0||(ce=ce.filter)===null||ce===void 0?void 0:ce.relation,oe)||(m("changeFilterRelation",oe),b(Lr(b(),["filter","relation"],oe,!0)),j()(b()))}),F(()=>t(y),()=>{var de,oe;de=t(y),et((oe=b())===null||oe===void 0||(oe=oe.filter)===null||oe===void 0?void 0:oe.value,de)||(m("changeFilterValue",de),b(Lr(b(),["filter","value"],de,!0)),j()(b()))}),F(()=>t(R),()=>{var de,oe,ce;oe=(de=t(R))===null||de===void 0?void 0:de.value,et((ce=b())===null||ce===void 0||(ce=ce.sort)===null||ce===void 0?void 0:ce.path,oe)||(m("changeSortPath",oe),b(Lr(b(),["sort","path"],oe,!0)),j()(b()))}),F(()=>t(N),()=>{var de,oe,ce;oe=(de=t(N))===null||de===void 0?void 0:de.value,et((ce=b())===null||ce===void 0||(ce=ce.sort)===null||ce===void 0?void 0:ce.direction,oe)||(m("changeSortDirection",oe),b(Lr(b(),["sort","direction"],oe,!0)),j()(b()))}),F(()=>t(p),()=>{(function(de){var oe;et((oe=b())===null||oe===void 0||(oe=oe.projection)===null||oe===void 0?void 0:oe.paths,de)||(m("changeProjectionPaths",de),b(Lr(b(),["projection","paths"],de,!0)),j()(b()))})(t(p)?t(p).map(de=>de.value):void 0)}),mt(),jn(!0);var _=fj(),z=M(_),W=M(z),X=q(M(W)),H=M(X),ne=M(H);Ta(ne,{class:"jse-filter-path",showChevron:!0,get items(){return t(u)},get value(){return t($)},set value(de){v($,de)},$$legacy:!0});var ye=q(ne,2);Ta(ye,{class:"jse-filter-relation",showChevron:!0,clearable:!1,items:x,get value(){return t(S)},set value(de){v(S,de)},$$legacy:!0});var te=q(ye,2),ve=q(W),Re=q(M(ve)),Ee=M(Re),fe=M(Ee);Ta(fe,{class:"jse-sort-path",showChevron:!0,get items(){return t(u)},get value(){return t(R)},set value(de){v(R,de)},$$legacy:!0}),Ta(q(fe,2),{class:"jse-sort-direction",showChevron:!0,clearable:!1,items:k,get value(){return t(N)},set value(de){v(N,de)},$$legacy:!0});var Se=q(ve),we=q(M(Se)),Ne=M(we);Ta(M(Ne),{class:"jse-projection-paths",multiple:!0,showChevron:!0,get items(){return t(h)},get value(){return t(p)},set value(de){v(p,de)},$$legacy:!0}),kl(te,()=>t(y),de=>v(y,de)),A(e,_),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-select-query-language.svelte-atm4um {
  position: relative;
  width: 32px;
}
.jse-select-query-language.svelte-atm4um .jse-select-query-language-container:where(.svelte-atm4um) {
  position: absolute;
  top: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
}
.jse-select-query-language.svelte-atm4um .jse-select-query-language-container:where(.svelte-atm4um) .jse-query-language:where(.svelte-atm4um) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  text-align: left;
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  white-space: nowrap;
  color: var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff));
  background: var(--jse-context-menu-background, #656565);
}
.jse-select-query-language.svelte-atm4um .jse-select-query-language-container:where(.svelte-atm4um) .jse-query-language:where(.svelte-atm4um):hover {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
}`);var hj=L('<button type="button"><!> </button>'),gj=L('<div class="jse-select-query-language svelte-atm4um"><div class="jse-select-query-language-container svelte-atm4um"></div></div>');function mj(e,n){en(n,!1);var o=f(n,"queryLanguages",8),r=f(n,"queryLanguageId",12),s=f(n,"onChangeQueryLanguage",8);jn();var a=gj();lo(M(a),5,o,ho,(i,l)=>{var c,d=hj(),u=M(d),h=g=>{nt(g,{data:_f})},p=g=>{nt(g,{data:Df})};Q(u,g=>{t(l).id===r()?g(h):g(p,!1)});var m=q(u);be(g=>{var b;c=kn(d,1,"jse-query-language svelte-atm4um",null,c,g),ht(d,"title","Select ".concat(t(l).name," as query language")),ln(m," ".concat((b=t(l).name)!==null&&b!==void 0?b:""))},[()=>({selected:t(l).id===r()})],ae),ie("click",d,()=>{return g=t(l).id,r(g),void s()(g);var g}),A(i,d)}),A(e,a),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-header.svelte-1y24war {
  display: flex;
  background: var(--jse-theme-color, #3883fa);
  color: var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
}
.jse-header.svelte-1y24war .jse-title:where(.svelte-1y24war) {
  flex: 1;
  padding: 5px;
  vertical-align: middle;
}
.jse-header.svelte-1y24war button:where(.svelte-1y24war) {
  border: none;
  background: transparent;
  min-width: 32px;
  color: inherit;
  cursor: pointer;
}
.jse-header.svelte-1y24war button:where(.svelte-1y24war):hover {
  background: rgba(255, 255, 255, 0.1);
}`);var bj=L('<button type="button" class="jse-fullscreen svelte-1y24war" title="Toggle full screen"><!></button>'),jj=L('<div class="jse-header svelte-1y24war"><div class="jse-title svelte-1y24war"> </div> <!> <!> <button type="button" class="jse-close svelte-1y24war"><!></button></div>');function Pl(e,n){en(n,!1);var o=f(n,"title",9,"Modal"),r=f(n,"fullScreenButton",9,!1),s=f(n,"fullscreen",13,!1),a=f(n,"onClose",9,void 0);jn(!0);var i=jj(),l=M(i),c=M(l),d=q(l,2);io(d,n,"actions",{},null);var u=q(d,2),h=m=>{var g=bj(),b=M(g),j=ae(()=>s()?im:lm);nt(b,{get data(){return t(j)}}),ie("click",g,()=>s(!s())),A(m,g)};Q(u,m=>{r()&&m(h)});var p=q(u,2);nt(M(p),{data:Ll}),be(()=>ln(c,o())),ie("click",p,()=>{var m;return(m=a())===null||m===void 0?void 0:m()}),A(e,i),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-config.svelte-1kpylsp {
  border: none;
  background: transparent;
  min-width: 32px;
  color: inherit;
  cursor: pointer;
}
.jse-config.svelte-1kpylsp:hover {
  background: rgba(255, 255, 255, 0.1);
}
.jse-config.hide.svelte-1kpylsp {
  display: none;
}`);var xj=L('<button slot="actions" type="button" title="Select a query language"><!></button>'),Jc=So("jsoneditor:AutoScrollHandler");function Yv(e){var n,o;function r(l){return l<20?200:l<50?400:1200}function s(){if(e){var l=.05*(n||0);e.scrollTop+=l}}function a(l){o&&l===n||(i(),Jc("startAutoScroll",l),n=l,o=setInterval(s,50))}function i(){o&&(Jc("stopAutoScroll"),clearInterval(o),o=void 0,n=void 0)}return Jc("createAutoScrollHandler",e),{onDrag:function(l){if(e){var c=l.clientY,{top:d,bottom:u}=e.getBoundingClientRect();c<d?a(-r(d-c)):c>u?a(r(c-u)):i()}},onDragEnd:function(){i()}}}var yj=(e,n,o,r)=>(e/=r/2)<1?o/2*e*e+n:-o/2*(--e*(e-2)-1)+n,kh=()=>{var e,n,o,r,s,a,i,l,c,d,u,h,p;function m(j){return j.getBoundingClientRect().top-(e.getBoundingClientRect?e.getBoundingClientRect().top:0)+o}function g(j){e.scrollTo?e.scrollTo(e.scrollLeft,j):e.scrollTop=j}function b(j){d||(d=j),g(a(u=j-d,o,l,c)),p=!0,u<c?requestAnimationFrame(b):function(){g(o+l),n&&i&&(n.setAttribute("tabindex","-1"),n.focus()),typeof h=="function"&&h(),d=0,p=!1}()}return function(j){var x=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{};switch(c=1e3,s=x.offset||0,h=x.callback,a=x.easing||yj,i=x.a11y||!1,typeof x.container){case"object":e=x.container;break;case"string":e=document.querySelector(x.container);break;default:e=window.document.documentElement}switch(o=e.scrollTop,typeof j){case"number":n=void 0,i=!1,r=o+j;break;case"object":r=m(n=j);break;case"string":n=document.querySelector(j),r=m(n)}switch(l=r-o+s,typeof x.duration){case"number":c=x.duration;break;case"function":c=x.duration(l)}p?d=0:requestAnimationFrame(b)}};function fs(e,n){var o=Date.now(),r=e();return n(Date.now()-o),r}var ls=So("validation"),wj={createObjectDocumentState:()=>({type:"object",properties:{}}),createArrayDocumentState:()=>({type:"array",items:[]}),createValueDocumentState:()=>({type:"value"})};function Kv(e,n,o,r){return vd(e,n,o,r,wj)}function Ch(e,n,o,r){if(ls("validateJSON"),!n)return[];if(o!==r){var s=o.stringify(e);return n(s!==void 0?r.parse(s):void 0)}return n(e)}function kj(e,n,o,r){if(ls("validateText"),e.length>104857600)return{validationErrors:[{path:[],message:"Validation turned off: the document is too large",severity:pr.info}]};if(e.length!==0)try{var s=fs(()=>o.parse(e),c=>ls("validate: parsed json in ".concat(c," ms")));if(!n)return;var a=o===r?s:fs(()=>r.parse(e),c=>ls("validate: parsed json with the validationParser in ".concat(c," ms"))),i=fs(()=>n(a),c=>ls("validate: validated json in ".concat(c," ms")));return Mt(i)?void 0:{validationErrors:i}}catch(c){var l=fs(()=>function(d,u){if(d.length>l0)return!1;try{return u.parse(Or(d)),!0}catch{return!1}}(e,o),d=>ls("validate: checked whether repairable in ".concat(d," ms")));return{parseError:Ss(e,c.message||c.toString()),isRepairable:l}}}var Zi=So("jsoneditor:FocusTracker");function jd(e){var n,{onMount:o,onDestroy:r,getWindow:s,hasFocus:a,onFocus:i,onBlur:l}=e,c=!1;function d(){var h=a();h&&(clearTimeout(n),c||(Zi("focus"),i(),c=h))}function u(){c&&(clearTimeout(n),n=setTimeout(()=>{a()||(Zi("blur"),c=!1,l())}))}o(()=>{Zi("mount FocusTracker");var h=s();h&&(h.addEventListener("focusin",d,!0),h.addEventListener("focusout",u,!0))}),r(()=>{Zi("destroy FocusTracker");var h=s();h&&(h.removeEventListener("focusin",d,!0),h.removeEventListener("focusout",u,!0))})}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-message.svelte-czprfx {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  padding: var(--jse-padding, 10px);
  display: flex;
  gap: var(--jse-padding, 10px);
  flex-wrap: wrap;
  align-items: stretch;
}
.jse-message.jse-success.svelte-czprfx {
  background: var(--message-success-background, #9ac45d);
  color: var(--jse-message-success-color, #fff);
}
.jse-message.svelte-czprfx .jse-text:where(.svelte-czprfx) {
  display: flex;
  flex: 1;
  min-width: 60%;
  align-items: center;
}
.jse-message.svelte-czprfx .jse-text.jse-clickable:where(.svelte-czprfx) {
  cursor: pointer;
}
.jse-message.svelte-czprfx .jse-text.jse-clickable:where(.svelte-czprfx):hover {
  background-color: rgba(255, 255, 255, 0.1);
}
.jse-message.jse-error.svelte-czprfx {
  background: var(--jse-message-error-background, var(--jse-error-color, #ee5341));
  color: var(--jse-message-error-color, #fff);
}
.jse-message.jse-warning.svelte-czprfx {
  background: var(--jse-message-warning-background, #ffde5c);
  color: var(--jse-message-warning-color, #4d4d4d);
}
.jse-message.jse-info.svelte-czprfx {
  background: var(--jse-message-info-background, #4f91ff);
  color: var(--jse-message-info-color, #fff);
}
.jse-message.svelte-czprfx .jse-actions:where(.svelte-czprfx) {
  display: flex;
  gap: var(--jse-padding, 10px);
}
.jse-message.svelte-czprfx .jse-actions:where(.svelte-czprfx) button.jse-action:where(.svelte-czprfx) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-message-action-background, rgba(255, 255, 255, 0.2));
  color: inherit;
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
}
.jse-message.svelte-czprfx .jse-actions:where(.svelte-czprfx) button.jse-action:where(.svelte-czprfx):hover {
  background: var(--jse-message-action-background-highlight, rgba(255, 255, 255, 0.3));
}`);var Cj=L('<button type="button" class="jse-button jse-action jse-primary svelte-czprfx"><!> </button>'),Sj=L('<div><div role="button" tabindex="-1"><div class="jse-text-centered"><!> </div></div> <div class="jse-actions svelte-czprfx"></div></div>');function jr(e,n){en(n,!1);var o=f(n,"type",9,"success"),r=f(n,"icon",9,void 0),s=f(n,"message",9,void 0),a=f(n,"actions",25,()=>[]),i=f(n,"onClick",9,void 0),l=f(n,"onClose",9,void 0);l()&&rr(l()),jn(!0);var c,d=Sj(),u=M(d),h=M(u),p=M(h),m=b=>{nt(b,{get data(){return r()}})};Q(p,b=>{r()&&b(m)});var g=q(p);lo(q(u,2),5,a,ho,(b,j)=>{var x=Cj(),k=M(x),$=y=>{nt(y,{get data(){return t(j).icon}})};Q(k,y=>{t(j).icon&&y($)});var S=q(k);be(()=>{var y;ht(x,"title",t(j).title),x.disabled=t(j).disabled,ln(S," ".concat((y=t(j).text)!==null&&y!==void 0?y:""))}),ie("click",x,()=>{t(j).onClick&&t(j).onClick()}),ie("mousedown",x,()=>{t(j).onMouseDown&&t(j).onMouseDown()}),A(b,x)}),be(b=>{var j,x;kn(d,1,"jse-message jse-".concat((j=o())!==null&&j!==void 0?j:""),"svelte-czprfx"),c=kn(u,1,"jse-text svelte-czprfx",null,c,b),ln(g," ".concat((x=s())!==null&&x!==void 0?x:""))},[()=>({"jse-clickable":!!i()})],ae),ie("click",u,function(){i()&&i()()}),A(e,d),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-validation-errors-overview.svelte-1uindol {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  overflow: auto;
  max-height: 25%;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) {
  border-collapse: collapse;
  width: 100%;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) {
  cursor: pointer;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr.jse-validation-error:where(.svelte-1uindol) {
  background: var(--jse-message-error-background, var(--jse-error-color, #ee5341));
  color: var(--jse-message-error-color, #fff);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr.jse-validation-warning:where(.svelte-1uindol) {
  background: var(--jse-message-warning-background, #ffde5c);
  color: var(--jse-message-warning-color, #4d4d4d);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr.jse-validation-warning:where(.svelte-1uindol):hover {
  filter: brightness(105%);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr.jse-validation-info:where(.svelte-1uindol) {
  background: var(--jse-message-info-background, #4f91ff);
  color: var(--jse-message-info-color, #fff);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol):hover {
  filter: brightness(110%);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td:where(.svelte-1uindol) {
  padding: 4px var(--jse-padding, 10px);
  vertical-align: middle;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td.jse-validation-error-icon:where(.svelte-1uindol) {
  width: 36px;
  box-sizing: border-box;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td.jse-validation-error-action:where(.svelte-1uindol) {
  width: 36px;
  box-sizing: border-box;
  padding: 0;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td.jse-validation-error-action:where(.svelte-1uindol) button.jse-validation-errors-collapse:where(.svelte-1uindol) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  width: 36px;
  height: 26px;
  cursor: pointer;
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td.jse-validation-error-action:where(.svelte-1uindol) button.jse-validation-errors-collapse:where(.svelte-1uindol):hover {
  background-color: rgba(255, 255, 255, 0.2);
}
.jse-validation-errors-overview.svelte-1uindol table:where(.svelte-1uindol) tr:where(.svelte-1uindol) td:where(.svelte-1uindol) div.jse-validation-errors-expand:where(.svelte-1uindol) {
  display: inline-block;
  position: relative;
  top: 3px;
}`);var zj=L('<button type="button" class="jse-validation-errors-collapse svelte-1uindol" title="Collapse validation errors"><!></button>'),Oj=L('<tr tabindex="0"><td class="jse-validation-error-icon svelte-1uindol"><!></td><td class="jse-validation-error-path svelte-1uindol"> </td><td class="jse-validation-error-message svelte-1uindol"> </td><td class="jse-validation-error-action svelte-1uindol"><!></td></tr>'),Mj=L('<tr class="jse-validation-error svelte-1uindol"><td class="svelte-1uindol"></td><td class="svelte-1uindol"></td><td class="svelte-1uindol"> </td><td class="svelte-1uindol"></td></tr>'),$j=L('<table class="jse-validation-errors-overview-expanded svelte-1uindol"><tbody><!><!></tbody></table>'),Ej=L('<table class="jse-validation-errors-overview-collapsed svelte-1uindol"><tbody><tr><td class="jse-validation-error-icon svelte-1uindol"><!></td><td class="jse-validation-error-count svelte-1uindol"> <div class="jse-validation-errors-expand svelte-1uindol"><!></div></td></tr></tbody></table>'),Aj=L('<div class="jse-validation-errors-overview svelte-1uindol"><!></div>');function xd(e,n){en(n,!1);var o=E(void 0,!0),r=f(n,"validationErrors",9),s=f(n,"selectError",9),a=E(!0,!0);function i(){v(a,!1)}function l(){v(a,!0)}F(()=>O(r()),()=>{v(o,r().length)}),mt(),jn(!0);var c=to(),d=rn(c),u=h=>{var p=Aj(),m=M(p),g=j=>{var x=$j(),k=M(x),$=M(k);lo($,1,()=>qp(r(),100),ho,(R,N,_)=>{var z=Oj(),W=M(z);nt(M(W),{data:_a});var X=q(W),H=M(X),ne=q(X),ye=M(ne),te=M(q(ne)),ve=Re=>{var Ee=zj();nt(M(Ee),{data:Rm}),ie("click",Ee,Jr(i)),A(Re,Ee)};Q(te,Re=>{_===0&&r().length>1&&Re(ve)}),be(Re=>{var Ee;kn(z,1,"jse-validation-".concat((Ee=t(N).severity)!==null&&Ee!==void 0?Ee:""),"svelte-1uindol"),ln(H,Re),ln(ye,t(N).message)},[()=>vr(t(N).path)],ae),ie("click",z,()=>{setTimeout(()=>s()(t(N)))}),A(R,z)});var S=q($),y=R=>{var N=Mj(),_=q(M(N),2),z=M(_);be(()=>ln(z,"(and ".concat(t(o)-100," more errors)"))),A(R,N)};Q(S,R=>{t(o)>100&&R(y)}),A(j,x)},b=j=>{var x=Ej(),k=M(x),$=M(k),S=M($);nt(M(S),{data:_a});var y=M(q(S));nt(M(q(y)),{data:Kf}),be(R=>{var N;kn($,1,"jse-validation-".concat(R??""),"svelte-1uindol"),ln(y,"".concat((N=t(o))!==null&&N!==void 0?N:""," validation errors "))},[()=>{return R=r(),[pr.error,pr.warning,pr.info].find(N=>R.some(_=>_.severity===N));var R}],ae),ie("click",$,l),A(j,x)};Q(m,j=>{t(a)||t(o)===1?j(g):j(b,!1)}),A(h,p)};Q(d,h=>{Mt(r())||h(u)}),A(e,c),nn()}function Il(e,n){if(e)return e.addEventListener("keydown",o),{destroy(){e.removeEventListener("keydown",o)}};function o(r){r.key==="Escape"&&(r.preventDefault(),r.stopPropagation(),n())}}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
dialog.jse-modal.svelte-1s9c2ql {
  border-radius: 3px;
  font-size: var(--jse-padding, 10px);
  border: none;
  padding: 0;
  display: flex;
  min-width: 0;
  margin: auto;
  overflow: visible;
  transition: width 0.1s ease-in-out, height 0.1s ease-in-out;
}
dialog.jse-modal.jse-sort-modal.svelte-1s9c2ql {
  width: 400px;
}
dialog.jse-modal.jse-repair-modal.svelte-1s9c2ql {
  width: 600px;
  height: 500px;
}
dialog.jse-modal.jse-jsoneditor-modal.svelte-1s9c2ql {
  width: 800px;
  height: 600px;
}
dialog.jse-modal.jse-transform-modal.svelte-1s9c2ql {
  width: 1200px;
  height: 800px;
}
dialog.jse-modal.jse-fullscreen.svelte-1s9c2ql {
  width: 100%;
  height: 100%;
}
dialog.jse-modal.svelte-1s9c2ql::backdrop {
  background: var(--jse-overlay-background, rgba(0, 0, 0, 0.3));
}
dialog.jse-modal[open].svelte-1s9c2ql {
  animation: svelte-1s9c2ql-zoom 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
dialog.jse-modal[open].svelte-1s9c2ql::backdrop {
  animation: svelte-1s9c2ql-fade 0.2s ease-out;
}
dialog.jse-modal.svelte-1s9c2ql .jse-modal-inner:where(.svelte-1s9c2ql) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 0;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  line-height: normal;
  background: var(--jse-modal-background, #f5f5f5);
  color: var(--jse-text-color, #4d4d4d);
}
@keyframes svelte-1s9c2ql-zoom {
  from {
    transform: scale(0.95);
  }
  to {
    transform: scale(1);
  }
}
@keyframes svelte-1s9c2ql-fade {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
dialog.jse-modal.svelte-1s9c2ql .svelte-select {
  --border: var(--jse-svelte-select-border, 1px solid #d8dbdf);
  --item-is-active-bg: var(--jse-item-is-active-bg, #3883fa);
  --border-radius: var(--jse-svelte-select-border-radius, 3px);
  --background: var(--jse-svelte-select-background, #fff);
  --padding: var(--jse-svelte-select-padding, 0 10px);
  --multi-select-padding: var(--jse-svelte-select-multi-select-padding, 0 10px);
  --font-size: var(--jse-svelte-select-font-size, var(--jse-font-size, 16px));
  --height: 36px;
  --multi-item-height: 28px;
  --multi-item-margin: 2px;
  --multi-item-padding: 2px 8px;
  --multi-item-border-radius: 6px;
  --indicator-top: 8px;
}`);var Rj=L('<dialog><div class="jse-modal-inner svelte-1s9c2ql"><!></div></dialog>');function zi(e,n){en(n,!1);var o=f(n,"className",8,void 0),r=f(n,"fullscreen",8,!1),s=f(n,"onClose",8),a=E();function i(){s()()}Po(()=>t(a).showModal()),rr(()=>t(a).close()),jn();var l,c=Rj(),d=M(c);io(M(d),n,"default",{},null),Yt(c,u=>v(a,u),()=>t(a)),$o(()=>ie("close",c,i)),$o(()=>{return ie("pointerdown",c,(u=i,function(){for(var h=arguments.length,p=new Array(h),m=0;m<h;m++)p[m]=arguments[m];p[0].target===this&&(u==null||u.apply(this,p))}));var u}),$o(()=>ie("cancel",c,ua(function(u){ai.call(this,n,u)}))),Uo(c,(u,h)=>Il==null?void 0:Il(u,h),()=>i),be((u,h)=>l=kn(c,1,u,"svelte-1s9c2ql",l,h),[()=>za(Fs("jse-modal",o())),()=>({"jse-fullscreen":r()})],ae),A(e,c),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-modal-contents.svelte-189qksl {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: auto;
  min-width: 0;
  min-height: 0;
}
.jse-modal-contents.svelte-189qksl .jse-actions:where(.svelte-189qksl) {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  padding-top: var(--jse-padding, 10px);
}
.jse-modal-contents.svelte-189qksl .jse-actions:where(.svelte-189qksl) button.jse-primary:where(.svelte-189qksl) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-modal-contents.svelte-189qksl .jse-actions:where(.svelte-189qksl) button.jse-primary:where(.svelte-189qksl):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-modal-contents.svelte-189qksl .jse-actions:where(.svelte-189qksl) button.jse-primary:where(.svelte-189qksl):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}

.jse-shortcuts.svelte-189qksl {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-around;
  margin: calc(2 * var(--jse-padding, 10px)) 0;
}
.jse-shortcuts.svelte-189qksl .jse-shortcut:where(.svelte-189qksl) .jse-key:where(.svelte-189qksl) {
  font-size: 200%;
  color: var(--jse-theme-color, #3883fa);
}`);var Tj=L('<!> <div class="jse-modal-contents svelte-189qksl"><div>Clipboard permission is disabled by your browser. You can use:</div> <div class="jse-shortcuts svelte-189qksl"><div class="jse-shortcut svelte-189qksl"><div class="jse-key svelte-189qksl"></div> for copy</div> <div class="jse-shortcut svelte-189qksl"><div class="jse-key svelte-189qksl"></div> for cut</div> <div class="jse-shortcut svelte-189qksl"><div class="jse-key svelte-189qksl"></div> for paste</div></div> <div class="jse-actions svelte-189qksl"><button type="button" class="jse-primary svelte-189qksl">Close</button></div></div>',1);function Sh(e,n){en(n,!1);var o=f(n,"onClose",9),r=rd()?"⌘":"Ctrl";jn(!0),zi(e,{get onClose(){return o()},className:"jse-copy-paste",children:(s,a)=>{var i=Tj(),l=rn(i);Pl(l,{title:"Copying and pasting",get onClose(){return o()}});var c=q(l,2),d=q(M(c),2),u=M(d);M(u).textContent="".concat(r??"","+C");var h=q(u,2);M(h).textContent="".concat(r??"","+X"),M(q(h,2)).textContent="".concat(r??"","+V"),ie("click",M(q(d,2)),function(){for(var p,m=arguments.length,g=new Array(m),b=0;b<m;b++)g[b]=arguments[b];(p=o())===null||p===void 0||p.apply(this,g)}),A(s,i)},$$slots:{default:!0}}),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-menu.svelte-pf7s2l {
  background: var(--jse-theme-color, #3883fa);
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size-main-menu, 14px);
  color: var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  position: relative;
}
.jse-menu.svelte-pf7s2l .jse-button:where(.svelte-pf7s2l) {
  font-family: inherit;
  font-size: inherit;
  line-height: 1.5em;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  width: var(--jse-menu-button-size, 32px);
  height: var(--jse-menu-button-size, 32px);
  padding: calc(0.5 * var(--jse-padding, 10px));
  margin: 0;
  border-radius: 0;
  display: inline-flex;
  align-items: center;
  text-align: center;
  justify-content: center;
}
.jse-menu.svelte-pf7s2l .jse-button:where(.svelte-pf7s2l):hover, .jse-menu.svelte-pf7s2l .jse-button:where(.svelte-pf7s2l):focus {
  background: var(--jse-theme-color-highlight, #5f9dff);
}
.jse-menu.svelte-pf7s2l .jse-button:where(.svelte-pf7s2l):disabled {
  color: var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
  opacity: 0.5;
  background: transparent;
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button:where(.svelte-pf7s2l) {
  width: auto;
  height: calc(var(--jse-menu-button-size, 32px) - var(--jse-padding, 10px));
  margin: calc(0.5 * var(--jse-padding, 10px)) 0;
  padding: 0 calc(0.5 * var(--jse-padding, 10px)) 1px;
  border: 1px solid var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button:where(.svelte-pf7s2l):not(.jse-last) {
  border-right: none;
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button.jse-first:where(.svelte-pf7s2l) {
  margin-left: calc(0.5 * var(--jse-padding, 10px));
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button.jse-last:where(.svelte-pf7s2l) {
  margin-right: calc(0.5 * var(--jse-padding, 10px));
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button:where(.svelte-pf7s2l):hover, .jse-menu.svelte-pf7s2l .jse-button.jse-group-button:where(.svelte-pf7s2l):focus {
  background: var(--jse-theme-color-highlight, #5f9dff);
}
.jse-menu.svelte-pf7s2l .jse-button.jse-group-button.jse-selected:where(.svelte-pf7s2l) {
  background: var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
  color: var(--jse-theme-color, #3883fa);
}
.jse-menu.svelte-pf7s2l .jse-space:where(.svelte-pf7s2l) {
  flex: 1;
}
.jse-menu.svelte-pf7s2l .jse-separator:where(.svelte-pf7s2l) {
  background: var(--jse-menu-color, var(--jse-text-color-inverse, #fff));
  opacity: 0.3;
  width: 1px;
  margin: 3px;
}`);var Pj=L('<div class="jse-separator svelte-pf7s2l"></div>'),Ij=L('<div class="jse-space svelte-pf7s2l"></div>'),qj=L('<button type="button"><!> <!></button>'),Nj=L('<div class="jse-menu svelte-pf7s2l"><!> <!> <!></div>');function sc(e,n){en(n,!1);var o=f(n,"items",25,()=>[]);jn(!0);var r=Nj(),s=M(r);io(s,n,"left",{},null);var a=q(s,2);lo(a,1,o,ho,(i,l)=>{var c=to(),d=rn(c),u=p=>{A(p,Pj())},h=(p,m)=>{var g=j=>{A(j,Ij())},b=(j,x)=>{var k=S=>{var y=qj(),R=M(y),N=W=>{nt(W,{get data(){return t(l).icon}})};Q(R,W=>{t(l).icon&&W(N)});var _=q(R,2),z=W=>{var X=zo();be(()=>ln(X,t(l).text)),A(W,X)};Q(_,W=>{t(l).text&&W(z)}),be(()=>{var W;kn(y,1,"jse-button ".concat((W=t(l).className)!==null&&W!==void 0?W:""),"svelte-pf7s2l"),ht(y,"title",t(l).title),y.disabled=t(l).disabled||!1}),ie("click",y,function(){for(var W,X=arguments.length,H=new Array(X),ne=0;ne<X;ne++)H[ne]=arguments[ne];(W=t(l).onClick)===null||W===void 0||W.apply(this,H)}),A(S,y)},$=S=>{var y=zo();be(R=>ln(y,R),[()=>function(R){return console.error("Unknown type of menu item",R),"???"}(t(l))],ae),A(S,y)};Q(j,S=>{ci(t(l))?S(k):S($,!1)},x)};Q(p,j=>{Kp(t(l))?j(g):j(b,!1)},m)};Q(d,p=>{sl(t(l))?p(u):p(h,!1)}),A(i,c)}),io(q(a,2),n,"right",{},null),A(e,r),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-json-repair-component.svelte-3golau {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--jse-background-color, #fff);
  color: var(--jse-text-color, #4d4d4d);
}
.jse-json-repair-component.svelte-3golau .jse-info:where(.svelte-3golau) {
  padding: calc(0.5 * var(--jse-padding, 10px));
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  vertical-align: center;
}
.jse-json-repair-component.svelte-3golau .jse-json-text:where(.svelte-3golau) {
  flex: 1;
  border: none;
  padding: 2px;
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  background: var(--jse-input-background, var(--jse-background-color, #fff));
  color: var(--jse-text-color, #4d4d4d);
  resize: none;
  outline: none;
}`);var Uj=L('<div slot="left" class="jse-info svelte-3golau">Repair invalid JSON, then click apply</div>'),_j=L('<div class="jse-json-repair-component svelte-3golau"><!> <!> <textarea class="jse-json-text svelte-3golau" autocomplete="off" autocapitalize="off" spellcheck="false"></textarea></div>');function Dj(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=E(void 0,!0),a=E(void 0,!0),i=E(void 0,!0),l=E(void 0,!0),c=f(n,"text",13,""),d=f(n,"readOnly",9,!1),u=f(n,"onParse",9),h=f(n,"onRepair",9),p=f(n,"onChange",9,void 0),m=f(n,"onApply",9),g=f(n,"onCancel",9),b=So("jsoneditor:JSONRepair"),j=E(void 0,!0);function x(){if(t(j)&&t(o)){var X=t(o).position!==void 0?t(o).position:0;t(j).setSelectionRange(X,X),t(j).focus()}}function k(){m()(c())}function $(){try{c(h()(c())),p()&&p()(c())}catch{}}var S=E(void 0,!0);F(()=>O(c()),()=>{v(o,function(X){try{return void u()(X)}catch(H){return Ss(X,H.message)}}(c()))}),F(()=>O(c()),()=>{v(r,function(X){try{return h()(X),!0}catch{return!1}}(c()))}),F(()=>t(o),()=>{b("error",t(o))}),F(()=>O(g()),()=>{v(S,[{type:"space"},{type:"button",icon:Ll,title:"Cancel repair",className:"jse-cancel",onClick:g()}])}),F(()=>Ld,()=>{v(s,{icon:Ld,text:"Show me",title:"Scroll to the error location",onClick:x})}),F(()=>ws,()=>{v(a,{icon:ws,text:"Auto repair",title:"Automatically repair JSON",onClick:$})}),F(()=>(t(r),t(s),t(a)),()=>{v(i,t(r)?[t(s),t(a)]:[t(s)])}),F(()=>O(d()),()=>{v(l,[{icon:Bu,text:"Apply",title:"Apply fixed JSON",disabled:d(),onClick:k}])}),mt(),jn(!0);var y=_j(),R=M(y);sc(R,{get items(){return t(S)},$$slots:{left:(X,H)=>{A(X,Uj())}}});var N=q(R,2),_=X=>{var H=ae(()=>"Cannot parse JSON: ".concat(t(o).message));jr(X,{type:"error",icon:_a,get message(){return t(H)},get actions(){return t(i)}})},z=X=>{jr(X,{type:"success",message:"JSON is valid now and can be parsed.",get actions(){return t(l)}})};Q(N,X=>{t(o)?X(_):X(z,!1)});var W=q(N,2);Yt(W,X=>v(j,X),()=>t(j)),be(()=>{W.readOnly=d(),La(W,c())}),ie("input",W,function(X){b("handleChange");var H=X.target.value;c()!==H&&(c(H),p()&&p()(c()))}),A(e,y),nn()}function zh(e,n){en(n,!1);var o=f(n,"text",13),r=f(n,"onParse",9),s=f(n,"onRepair",9),a=f(n,"onApply",9),i=f(n,"onClose",9);function l(d){a()(d),i()()}function c(){i()()}jn(!0),zi(e,{get onClose(){return i()},className:"jse-repair-modal",children:(d,u)=>{Dj(d,{get onParse(){return r()},get onRepair(){return s()},onApply:l,onCancel:c,get text(){return o()},set text(h){o(h)},$$legacy:!0})},$$slots:{default:!0}}),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
div.jse-collapsed-items.svelte-1h6hzoq {
  margin-left: calc(var(--level) * var(--jse-indent-size, calc(1em + 4px)));
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  color: var(--jse-collapsed-items-link-color, rgba(0, 0, 0, 0.38));
  padding: calc(0.5 * var(--jse-padding, 10px));
  border: 8px solid transparent;
  border-width: 8px 0;
  background-color: var(--jse-contents-background-color, transparent);
  background-image: linear-gradient(var(--jse-collapsed-items-background-color, #f5f5f5), var(--jse-collapsed-items-background-color, #f5f5f5)), linear-gradient(to bottom right, transparent 50.5%, var(--jse-collapsed-items-background-color, #f5f5f5) 50.5%), linear-gradient(to bottom left, transparent 50.5%, var(--jse-collapsed-items-background-color, #f5f5f5) 50.5%), linear-gradient(to top right, transparent 50.5%, var(--jse-collapsed-items-background-color, #f5f5f5) 50.5%), linear-gradient(to top left, transparent 50.5%, var(--jse-collapsed-items-background-color, #f5f5f5) 50.5%);
  background-repeat: repeat, repeat-x, repeat-x, repeat-x, repeat-x;
  background-position: 0 0, 8px 0, 8px 0, 8px 100%, 8px 100%;
  background-size: auto auto, 16px 16px, 16px 16px, 16px 16px, 16px 16px;
  background-clip: padding-box, border-box, border-box, border-box, border-box;
  background-origin: padding-box, border-box, border-box, border-box, border-box;
  display: flex;
}
div.jse-collapsed-items.jse-selected.svelte-1h6hzoq {
  background-color: var(--jse-selection-background-color, #d3d3d3);
  --jse-collapsed-items-background-color: var(--jse-collapsed-items-selected-background-color, #c2c2c2);
}
div.jse-collapsed-items.svelte-1h6hzoq div.jse-text:where(.svelte-1h6hzoq),
div.jse-collapsed-items.svelte-1h6hzoq button.jse-expand-items:where(.svelte-1h6hzoq) {
  margin: 0 calc(0.5 * var(--jse-padding, 10px));
}
div.jse-collapsed-items.svelte-1h6hzoq div.jse-text:where(.svelte-1h6hzoq) {
  display: inline;
}
div.jse-collapsed-items.svelte-1h6hzoq button.jse-expand-items:where(.svelte-1h6hzoq) {
  font-family: inherit;
  font-size: inherit;
  color: var(--jse-collapsed-items-link-color, rgba(0, 0, 0, 0.38));
  background: none;
  border: none;
  padding: 0;
  text-decoration: underline;
  cursor: pointer;
}
div.jse-collapsed-items.svelte-1h6hzoq button.jse-expand-items:where(.svelte-1h6hzoq):hover, div.jse-collapsed-items.svelte-1h6hzoq button.jse-expand-items:where(.svelte-1h6hzoq):focus {
  color: var(--jse-collapsed-items-link-color-highlight, #ee5341);
}`);var Fj=L('<button type="button" class="jse-expand-items svelte-1h6hzoq"> </button>'),Lj=L('<div role="none"><div><div class="jse-text svelte-1h6hzoq"> </div> <!></div></div>');function Jj(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=E(void 0,!0),a=E(void 0,!0),i=E(void 0,!0),l=f(n,"visibleSections",9),c=f(n,"sectionIndex",9),d=f(n,"total",9),u=f(n,"path",9),h=f(n,"selection",9),p=f(n,"onExpandSection",9),m=f(n,"context",9);F(()=>(O(l()),O(c())),()=>{v(o,l()[c()])}),F(()=>t(o),()=>{v(r,t(o).end)}),F(()=>(O(l()),O(c()),O(d())),()=>{v(s,l()[c()+1]?l()[c()+1].start:d())}),F(()=>(O(m()),O(h()),O(u()),t(r)),()=>{v(a,ki(m().getJson(),h(),u().concat(String(t(r)))))}),F(()=>(t(r),t(s)),()=>{v(i,function(S,y){var R={start:S,end:Math.min(hu(S),y)},N=Math.max(Cl((S+y)/2),S),_={start:N,end:Math.min(hu(N),y)},z=Cl(y),W=z===y?z-ji:z,X={start:Math.max(W,S),end:y},H=[R],ne=_.start>=R.end&&_.end<=X.start;return ne&&H.push(_),X.start>=(ne?_.end:R.end)&&H.push(X),H}(t(r),t(s)))}),mt(),jn(!0);var g,b,j=Lj(),x=M(j),k=M(x),$=M(k);lo(q(k,2),1,()=>t(i),ho,(S,y)=>{var R=Fj(),N=M(R);be(()=>{var _,z;return ln(N,"show ".concat((_=t(y).start)!==null&&_!==void 0?_:"","-").concat((z=t(y).end)!==null&&z!==void 0?z:""))}),ie("click",R,()=>p()(u(),t(y))),A(S,R)}),be(S=>{var y,R;g=kn(j,1,"jse-collapsed-items svelte-1h6hzoq",null,g,S),b=fr(j,"",b,{"--level":u().length+2}),ln($,"Items ".concat((y=t(r))!==null&&y!==void 0?y:"","-").concat((R=t(s))!==null&&R!==void 0?R:""))},[()=>({"jse-selected":t(a)})],ae),ie("mousemove",j,function(S){S.stopPropagation()}),A(e,j),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-context-menu-pointer.svelte-137iwnw {
  position: absolute;
  top: calc(-0.5 * var(--jse-context-menu-pointer-size, calc(1em + 4px)));
  right: calc(-0.5 * var(--jse-context-menu-pointer-size, calc(1em + 4px)));
  width: var(--jse-context-menu-pointer-size, calc(1em + 4px));
  height: var(--jse-context-menu-pointer-size, calc(1em + 4px));
  padding: 0;
  margin: 0;
  cursor: pointer;
  background: transparent;
  border-radius: 2px;
  background: var(--jse-context-menu-pointer-hover-background, #b2b2b2);
  color: var(--jse-context-menu-pointer-color, var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff)));
  border: none;
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
}
.jse-context-menu-pointer.jse-root.svelte-137iwnw {
  top: 0;
  right: calc(-2px - var(--jse-context-menu-pointer-size, calc(1em + 4px)));
}
.jse-context-menu-pointer.jse-insert.svelte-137iwnw {
  right: -1px;
}
.jse-context-menu-pointer.svelte-137iwnw:hover {
  background: var(--jse-context-menu-pointer-background-highlight, var(--jse-context-menu-background-highlight, #7a7a7a));
}
.jse-context-menu-pointer.jse-selected.svelte-137iwnw {
  background: var(--jse-context-menu-pointer-background, var(--jse-context-menu-background, #656565));
}
.jse-context-menu-pointer.jse-selected.svelte-137iwnw:hover {
  background: var(--jse-context-menu-pointer-background-highlight, var(--jse-context-menu-background-highlight, #7a7a7a));
}`);var Bj=L('<button type="button"><!></button>');function da(e,n){en(n,!1);var o=f(n,"root",9,!1),r=f(n,"insert",9,!1),s=f(n,"selected",9),a=f(n,"onContextMenu",9);jn(!0);var i,l=Bj();ht(l,"title",sd),nt(M(l),{data:Ca}),be(c=>i=kn(l,1,"jse-context-menu-pointer svelte-137iwnw",null,i,c),[()=>({"jse-root":o(),"jse-insert":r(),"jse-selected":s()})],ae),ie("click",l,function(c){for(var d=c.target;d&&d.nodeName!=="BUTTON";)d=d.parentNode;d&&a()({anchor:d,left:0,top:0,width:Wr,height:Br,offsetTop:2,offsetLeft:0,showTip:!0})}),A(e,l),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-key.svelte-2iqnqn {
  display: inline-block;
  min-width: 2em;
  padding: 0 5px;
  box-sizing: border-box;
  outline: none;
  border-radius: 1px;
  vertical-align: top;
  color: var(--jse-key-color, #1a1a1a);
  word-break: normal;
  overflow-wrap: normal;
  white-space: pre-wrap;
}
.jse-key.jse-empty.svelte-2iqnqn {
  min-width: 3em;
  outline: 1px dotted var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  -moz-outline-radius: 2px;
}
.jse-key.jse-empty.svelte-2iqnqn::after {
  pointer-events: none;
  color: var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  content: "key";
}`);var Wj=L('<div role="none" data-type="selectable-key"><!></div>'),Vj=L("<!> <!>",1),Hj=L('<div role="button" tabindex="-1" class="jse-value" data-type="selectable-value"></div>');function Oh(e,n){en(n,!0);var o=Wo(()=>wt(n.selection)&&Vo(n.selection)),r=Wo(()=>n.context.onRenderValue({path:n.path,value:n.value,mode:n.context.mode,truncateTextSize:n.context.truncateTextSize,readOnly:n.context.readOnly,enforceString:n.enforceString,isEditing:t(o),parser:n.context.parser,normalization:n.context.normalization,selection:n.selection,searchResultItems:n.searchResultItems,onPatch:n.context.onPatch,onPasteJson:n.context.onPasteJson,onSelect:n.context.onSelect,onFind:n.context.onFind,findNextInside:n.context.findNextInside,focus:n.context.focus})),s=to();lo(rn(s),17,()=>t(r),ho,(a,i)=>{var l=to(),c=rn(l),d=h=>{var p=Hj(),m=Wo(()=>t(i).action);Uo(p,(g,b)=>{var j;return(j=t(m))===null||j===void 0?void 0:j(g,b)},()=>t(i).props),A(h,p)},u=h=>{var p=to(),m=Wo(()=>t(i).component);Mp(rn(p),()=>t(m),(g,b)=>{b(g,ya(()=>t(i).props))}),A(h,p)};Q(c,h=>{h0(t(i))?h(d):h(u,!1)}),A(a,l)}),A(e,s),nn()}var Yj={selecting:!1,selectionAnchor:void 0,selectionAnchorType:void 0,selectionFocus:void 0,dragging:!1};function Bc(e){var{json:n,selection:o,deltaY:r,items:s}=e;if(!o)return{operations:void 0,updatedSelection:void 0,offset:0};var a=r<0?function(u){for(var{json:h,items:p,selection:m,deltaY:g}=u,b=Vr(h,m),j=p.findIndex(R=>et(R.path,b)),x=()=>{var R;return(R=p[k-1])===null||R===void 0?void 0:R.height},k=j,$=0;x()!==void 0&&Math.abs(g)>$+x()/2;)$+=x(),k-=1;var S=p[k].path,y=k-j;return k!==j&&p[k]!==void 0?{beforePath:S,offset:y}:void 0}({json:n,selection:o,deltaY:r,items:s}):function(u){for(var h,{json:p,items:m,selection:g,deltaY:b}=u,j=ka(p,g),x=m.findIndex(W=>et(W.path,j)),k=0,$=x,S=()=>{var W;return(W=m[$+1])===null||W===void 0?void 0:W.height};S()!==void 0&&Math.abs(b)>k+S()/2;)k+=S(),$+=1;var y=ot(j),R=Je(p,y),N=Array.isArray(R)?$:$+1,_=(h=m[N])===null||h===void 0?void 0:h.path,z=$-x;return _?{beforePath:_,offset:z}:{append:!0,offset:z}}({json:n,selection:o,deltaY:r,items:s});if(!a||a.offset===0)return{operations:void 0,updatedSelection:void 0,offset:0};var i=function(u,h,p){if(!h)return[];var m="beforePath"in p?p.beforePath:void 0,g="append"in p?p.append:void 0,b=ot(Be(h)),j=Je(u,b);if(!(g||m&&oa(m,b)&&m.length>b.length))return[];var x=Vr(u,h),k=ka(u,h),$=Kn(x),S=Kn(k),y=m?m[b.length]:void 0;if(!Oo(j)){if(wo(j)){var R=Eo($),N=Eo(S),_=y!==void 0?Eo(y):j.length;return Im(N-R+1,_<R?ne=>({op:"move",from:Sn(b.concat(String(R+ne))),path:Sn(b.concat(String(_+ne)))}):()=>({op:"move",from:Sn(b.concat(String(R))),path:Sn(b.concat(String(_)))}))}throw new Error("Cannot create move operations: parent must be an Object or Array")}var z=Object.keys(j),W=z.indexOf($),X=z.indexOf(S),H=g?z.length:y!==void 0?z.indexOf(y):-1;return W!==-1&&X!==-1&&H!==-1?H>W?[...z.slice(W,X+1),...z.slice(H,z.length)].map(ne=>Wa(b,ne)):[...z.slice(H,W),...z.slice(X+1,z.length)].map(ne=>Wa(b,ne)):[]}(n,o,a),l=ot(Vr(n,o)),c=Je(n,l);if(Array.isArray(c)){var d=function(u){var h,p,{items:m,json:g,selection:b,offset:j}=u,x=Vr(g,b),k=ka(g,b),$=m.findIndex(N=>et(N.path,x)),S=m.findIndex(N=>et(N.path,k)),y=(h=m[$+j])===null||h===void 0?void 0:h.path,R=(p=m[S+j])===null||p===void 0?void 0:p.path;return qo(y,R)}({items:s,json:n,selection:o,offset:a.offset});return{operations:i,updatedSelection:d,offset:a.offset}}return{operations:i,updatedSelection:void 0,offset:a.offset}}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
button.jse-validation-error.svelte-1a8aobl {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  padding: 0;
  margin: 0;
  vertical-align: top;
  display: inline-flex;
  color: var(--jse-error-color, #ee5341);
}

button.jse-validation-info.svelte-1a8aobl {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  padding: 0;
  margin: 0;
  vertical-align: top;
  display: inline-flex;
  color: var(--jse-info-color, #4f91ff);
}

button.jse-validation-warning.svelte-1a8aobl {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  padding: 0;
  margin: 0;
  vertical-align: top;
  display: inline-flex;
  color: var(--jse-warning-color, #fdc539);
}`);var Kj=L('<button type="button"><!></button>');function xs(e,n){en(n,!1);var o=E(),r=Ma("absolute-popup"),s=f(n,"validationError",8),a=f(n,"onExpand",8);F(()=>O(s()),()=>{v(o,p0(s())&&s().isChildError?"Contains invalid data":s().message)}),mt(),jn();var i=Kj();nt(M(i),{data:_a}),$o(()=>ie("click",i,function(){for(var l,c=arguments.length,d=new Array(c),u=0;u<c;u++)d[u]=arguments[u];(l=a())===null||l===void 0||l.apply(this,d)})),Uo(i,(l,c)=>Ms==null?void 0:Ms(l,c),()=>me({text:t(o)},r)),be(()=>{var l;return kn(i,1,"jse-validation-".concat((l=s().severity)!==null&&l!==void 0?l:""),"svelte-1a8aobl")}),A(e,i),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-expand.svelte-oawf7x {
  width: var(--jse-indent-size, calc(1em + 4px));
  padding: 0;
  margin: 0;
  border: none;
  cursor: pointer;
  background: transparent;
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
  font-size: var(--jse-font-size-mono, 14px);
  height: var(--jse-line-height, calc(1em + 4px));
}
.jse-expand.svelte-oawf7x:hover {
  opacity: 0.8;
}

.jse-meta.svelte-oawf7x,
.jse-separator.svelte-oawf7x,
.jse-index.svelte-oawf7x,
.jse-bracket.svelte-oawf7x {
  vertical-align: top;
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
}

.jse-index.svelte-oawf7x {
  padding: 0 calc(0.5 * var(--jse-padding, 10px));
}

.jse-bracket.svelte-oawf7x {
  padding: 0 2px;
}
.jse-bracket.jse-expanded.svelte-oawf7x {
  padding-right: var(--jse-padding, 10px);
}

.jse-identifier.svelte-oawf7x {
  vertical-align: top;
  position: relative;
}

.jse-json-node.svelte-oawf7x {
  position: relative;
  color: var(--jse-text-color, #4d4d4d);
}
.jse-json-node.jse-root.svelte-oawf7x {
  min-height: 100%;
  padding-bottom: 2px;
  box-sizing: border-box;
}
.jse-json-node.jse-root.svelte-oawf7x > .jse-contents-outer:where(.svelte-oawf7x) > .jse-contents:where(.svelte-oawf7x) {
  padding-left: 0;
}
.jse-json-node.svelte-oawf7x .jse-props:where(.svelte-oawf7x),
.jse-json-node.svelte-oawf7x .jse-items:where(.svelte-oawf7x) {
  position: relative;
}
.jse-json-node.svelte-oawf7x .jse-header-outer:where(.svelte-oawf7x),
.jse-json-node.svelte-oawf7x .jse-footer-outer:where(.svelte-oawf7x) {
  display: flex;
  margin-left: calc(var(--level) * var(--jse-indent-size, calc(1em + 4px)));
}
.jse-json-node.svelte-oawf7x .jse-header:where(.svelte-oawf7x) {
  position: relative;
}
.jse-json-node.svelte-oawf7x .jse-header:where(.svelte-oawf7x) .jse-meta:where(.svelte-oawf7x) > .jse-meta-inner:where(.svelte-oawf7x) {
  display: flex;
  justify-content: center;
}
.jse-json-node.svelte-oawf7x .jse-contents-outer:where(.svelte-oawf7x) {
  display: flex;
  margin-left: calc(var(--level) * var(--jse-indent-size, calc(1em + 4px)));
}
.jse-json-node.svelte-oawf7x .jse-header:where(.svelte-oawf7x),
.jse-json-node.svelte-oawf7x .jse-contents:where(.svelte-oawf7x) {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}
.jse-json-node.svelte-oawf7x .jse-contents:where(.svelte-oawf7x) {
  padding-left: var(--jse-indent-size, calc(1em + 4px));
  cursor: var(--jse-contents-cursor, pointer);
}
.jse-json-node.svelte-oawf7x .jse-contents:where(.svelte-oawf7x) .jse-value-outer:where(.svelte-oawf7x) {
  display: inline-flex;
}
.jse-json-node.svelte-oawf7x .jse-footer:where(.svelte-oawf7x) {
  display: inline-flex;
  padding-left: calc(var(--jse-indent-size, calc(1em + 4px)) + 5px);
}
.jse-json-node.svelte-oawf7x .jse-header:where(.svelte-oawf7x),
.jse-json-node.svelte-oawf7x .jse-contents:where(.svelte-oawf7x),
.jse-json-node.svelte-oawf7x .jse-footer:where(.svelte-oawf7x) {
  background: var(--jse-contents-background-color, transparent);
}
.jse-json-node.svelte-oawf7x .jse-insert-selection-area:where(.svelte-oawf7x) {
  padding: 0 calc(0.5 * var(--jse-padding, 10px));
  flex: 1;
}
.jse-json-node.svelte-oawf7x .jse-insert-selection-area.jse-inside:where(.svelte-oawf7x) {
  display: inline-flex;
  align-items: center;
}
.jse-json-node.svelte-oawf7x .jse-insert-selection-area.jse-after:where(.svelte-oawf7x) {
  display: flex;
  align-items: flex-end;
}
.jse-json-node.svelte-oawf7x .jse-context-menu-pointer-anchor:where(.svelte-oawf7x) {
  position: relative;
}
.jse-json-node.svelte-oawf7x .jse-insert-area:where(.svelte-oawf7x) {
  display: flex;
  position: relative;
  z-index: 1;
  margin-left: calc(var(--level) * var(--jse-indent-size, calc(1em + 4px)));
  max-width: 250px;
  min-width: 100px;
  height: 0;
  margin-right: calc(0.5 * var(--jse-padding, 10px));
  outline: 1px solid;
}
.jse-json-node.svelte-oawf7x .jse-insert-area.jse-hovered:where(.svelte-oawf7x) {
  outline-color: var(--jse-context-menu-pointer-hover-background, #b2b2b2);
}
.jse-json-node.svelte-oawf7x .jse-key-outer:where(.svelte-oawf7x) {
  position: relative;
}
.jse-json-node.svelte-oawf7x .jse-key-outer:where(.svelte-oawf7x):hover,
.jse-json-node.svelte-oawf7x .jse-value-outer:where(.svelte-oawf7x):hover,
.jse-json-node.svelte-oawf7x .jse-meta:where(.svelte-oawf7x):hover,
.jse-json-node.svelte-oawf7x .jse-footer:where(.svelte-oawf7x):hover {
  background: var(--jse-hover-background-color, rgba(0, 0, 0, 0.06));
  cursor: var(--jse-contents-cursor, pointer);
}
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-header,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-contents,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-header,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-contents,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-footer {
  background: var(--jse-hover-background-color, rgba(0, 0, 0, 0.06));
  cursor: var(--jse-contents-cursor, pointer);
}
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-value-outer .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-value-outer .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-meta .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-meta .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-header .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-header .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-contents .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-items .jse-contents .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-header .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-header .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-contents .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-props .jse-contents .jse-meta,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-footer .jse-value-outer,
.jse-json-node.jse-hovered.svelte-oawf7x:not(.jse-selected):not(.jse-selected-value) .jse-footer .jse-meta {
  background: none;
}
.jse-json-node.jse-selected.svelte-oawf7x .jse-header:where(.svelte-oawf7x),
.jse-json-node.jse-selected.svelte-oawf7x .jse-contents:where(.svelte-oawf7x),
.jse-json-node.jse-selected.svelte-oawf7x .jse-footer:where(.svelte-oawf7x) {
  background: var(--jse-selection-background-color, #d3d3d3);
  cursor: var(--jse-contents-selected-cursor, grab);
}
.jse-json-node.jse-selected.svelte-oawf7x .jse-key-outer:where(.svelte-oawf7x):hover,
.jse-json-node.jse-selected.svelte-oawf7x .jse-value-outer:where(.svelte-oawf7x):hover,
.jse-json-node.jse-selected.svelte-oawf7x .jse-meta:where(.svelte-oawf7x):hover,
.jse-json-node.jse-selected.svelte-oawf7x .jse-footer:where(.svelte-oawf7x):hover {
  background: inherit;
  cursor: inherit;
}
.jse-json-node.svelte-oawf7x .jse-key-outer.jse-selected-key:where(.svelte-oawf7x) {
  background: var(--jse-selection-background-color, #d3d3d3);
  cursor: var(--jse-contents-selected-cursor, grab);
}
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-value-outer,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-meta,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-items .jse-header,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-items .jse-contents,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-props .jse-header,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-props .jse-contents,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-footer {
  background: var(--jse-selection-background-color, #d3d3d3);
  cursor: var(--jse-contents-selected-cursor, grab);
}
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-value-outer .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-meta .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-items .jse-header .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-items .jse-contents .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-props .jse-header .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-props .jse-contents .jse-key-outer:hover,
.jse-json-node.jse-selected-value.svelte-oawf7x .jse-footer .jse-key-outer:hover {
  background: inherit;
  cursor: inherit;
}
.jse-json-node.jse-readonly.svelte-oawf7x {
  --jse-contents-selected-cursor: pointer;
}
.jse-json-node.svelte-oawf7x .jse-insert-area.jse-selected:where(.svelte-oawf7x) {
  outline-color: var(--jse-context-menu-pointer-background, var(--jse-context-menu-background, #656565));
}`);var Vt=Gl(()=>Yj),Qj=L('<div class="jse-separator svelte-oawf7x">:</div>'),Gj=L('<div class="jse-bracket svelte-oawf7x">[</div> <!> &nbsp;',1),Xj=L('<div class="jse-bracket svelte-oawf7x">[</div> <!> <div class="jse-bracket svelte-oawf7x">]</div>',1),Zj=L('<div class="jse-context-menu-pointer-anchor svelte-oawf7x"><!></div>'),ex=L('<div role="none" class="jse-insert-selection-area jse-inside svelte-oawf7x" data-type="insert-selection-area-inside"></div>'),nx=L('<div role="none" class="jse-insert-selection-area jse-after svelte-oawf7x" data-type="insert-selection-area-after"></div>'),tx=L('<div data-type="insert-selection-area-inside"><!></div>'),ox=L('<div slot="identifier" class="jse-identifier svelte-oawf7x"><div class="jse-index svelte-oawf7x"> </div></div>'),rx=L("<!> <!>",1),ax=L('<div role="none" class="jse-insert-selection-area jse-after svelte-oawf7x" data-type="insert-selection-area-after"></div>'),sx=L('<div class="jse-items svelte-oawf7x"><!> <!></div> <div class="jse-footer-outer svelte-oawf7x"><div data-type="selectable-value" class="jse-footer svelte-oawf7x"><span class="jse-bracket svelte-oawf7x">]</span></div> <!></div>',1),ix=L('<div class="jse-header-outer svelte-oawf7x"><div class="jse-header svelte-oawf7x"><button type="button" class="jse-expand svelte-oawf7x" title="Expand or collapse this array (Ctrl+Click to expand/collapse recursively)"><!></button> <!> <!> <div class="jse-meta svelte-oawf7x"><div class="jse-meta-inner svelte-oawf7x" data-type="selectable-value"><!></div></div> <!></div> <!> <!></div> <!>',1),lx=L('<div class="jse-separator svelte-oawf7x">:</div>'),cx=L('<div class="jse-bracket jse-expanded svelte-oawf7x">&lbrace;</div>'),ux=L('<div class="jse-bracket svelte-oawf7x">&lbrace;</div> <!> <div class="jse-bracket svelte-oawf7x">&rbrace;</div>',1),dx=L('<div class="jse-context-menu-pointer-anchor svelte-oawf7x"><!></div>'),vx=L('<div role="none" class="jse-insert-selection-area jse-inside svelte-oawf7x" data-type="insert-selection-area-inside"></div>'),fx=L('<div role="none" class="jse-insert-selection-area jse-after svelte-oawf7x" data-type="insert-selection-area-after"></div>'),px=L('<div data-type="insert-selection-area-inside"><!></div>'),hx=L('<div slot="identifier"><!></div>'),gx=L('<div role="none" class="jse-insert-selection-area jse-after svelte-oawf7x" data-type="insert-selection-area-after"></div>'),mx=L('<div class="jse-props svelte-oawf7x"><!> <!></div> <div class="jse-footer-outer svelte-oawf7x"><div data-type="selectable-value" class="jse-footer svelte-oawf7x"><div class="jse-bracket svelte-oawf7x">&rbrace;</div></div> <!></div>',1),bx=L('<div class="jse-header-outer svelte-oawf7x"><div class="jse-header svelte-oawf7x"><button type="button" class="jse-expand svelte-oawf7x" title="Expand or collapse this object (Ctrl+Click to expand/collapse recursively)"><!></button> <!> <!> <div class="jse-meta svelte-oawf7x" data-type="selectable-value"><div class="jse-meta-inner svelte-oawf7x"><!></div></div> <!></div> <!> <!></div> <!>',1),jx=L('<div class="jse-separator svelte-oawf7x">:</div>'),xx=L('<div class="jse-context-menu-pointer-anchor svelte-oawf7x"><!></div>'),yx=L('<div role="none" class="jse-insert-selection-area jse-after svelte-oawf7x" data-type="insert-selection-area-after"></div>'),wx=L('<div class="jse-contents-outer svelte-oawf7x"><div class="jse-contents svelte-oawf7x"><!> <!> <div class="jse-value-outer svelte-oawf7x"><!></div> <!></div> <!> <!></div>'),kx=L('<div data-type="insert-selection-area-after"><!></div>'),Cx=L('<div role="treeitem" tabindex="-1"><!> <!></div>');function zu(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=f(n,"pointer",9),a=f(n,"value",9),i=f(n,"state",9),l=f(n,"validationErrors",9),c=f(n,"searchResults",9),d=f(n,"selection",9),u=f(n,"context",9),h=f(n,"onDragSelectionStart",9),p=So("jsoneditor:JSONNode"),m=E(void 0,!0),g=void 0,b=E(void 0,!0),j=E(void 0,!0),x=E(void 0,!0),k=E(void 0,!0),$=E(void 0,!0),S=E(void 0,!0),y=E(void 0,!0);function R(Y){Y.stopPropagation();var I=ad(Y);u().onExpand(t(j),!t(x),I)}function N(){u().onExpand(t(j),!0)}function _(Y,I){var ee=Di(t(j),Object.keys(a()),Y,I);return u().onPatch(ee),Kn(Sr(ee[0].path))}function z(Y){u().onDrag(Y)}function W(Y){Vt().selecting&&(Vt(Vt().selecting=!1),Y.stopPropagation()),u().onDragEnd(),document.removeEventListener("mousemove",z,!0),document.removeEventListener("mouseup",W)}function X(){var Y;return((Y=u().findElement([]))===null||Y===void 0||(Y=Y.getBoundingClientRect())===null||Y===void 0?void 0:Y.top)||0}function H(Y,I){var ee=X()-Y.initialContentTop;return I.clientY-Y.initialClientY-ee}function ne(Y){if(!u().readOnly&&d()){var I=ot(Be(d()));if(et(t(j),I)){var ee=function(V,T){var xn=[];function Qe(qe){var Ae=t(j).concat(qe),Ue=u().findElement(Ae);Ue!==void 0&&xn.push({path:Ae,height:Ue.clientHeight})}if(Array.isArray(a())){var cn=u().getJson();if(cn===void 0)return;var sn=Vr(cn,V),je=ka(cn,V),Xe=parseInt(Kn(sn),10),$n=parseInt(Kn(je),10),De=T.find(qe=>Xe>=qe.start&&$n<=qe.end);if(!De)return;var{start:Nn,end:ge}=De;Ip(Nn,Math.min(a().length,ge),qe=>Qe(String(qe)))}else Object.keys(a()).forEach(Qe);return xn}(d(),t($)||ms);if(p("dragSelectionStart",{selection:d(),items:ee}),ee){var P=u().getJson();if(P!==void 0){var pe=Vr(P,d()),K=ee.findIndex(V=>et(V.path,pe)),{offset:Me}=Bc({json:P,selection:u().getSelection(),deltaY:0,items:ee});v(b,{initialTarget:Y.target,initialClientY:Y.clientY,initialContentTop:X(),selectionStartIndex:K,selectionItemsCount:Oa(P,d()).length,items:ee,offset:Me,didMoveItems:!1}),Vt(Vt().dragging=!0),document.addEventListener("mousemove",ye,!0),document.addEventListener("mouseup",te)}}else p("Cannot drag the current selection (probably spread over multiple sections)")}else h()(Y)}}function ye(Y){if(t(b)){var I=u().getJson();if(I===void 0)return;var ee=H(t(b),Y),{offset:P}=Bc({json:I,selection:u().getSelection(),deltaY:ee,items:t(b).items});P!==t(b).offset&&(p("drag selection",P,ee),v(b,me(me({},t(b)),{},{offset:P,didMoveItems:!0})))}}function te(Y){if(t(b)){var I=u().getJson();if(I===void 0)return;var ee=H(t(b),Y),{operations:P,updatedSelection:pe}=Bc({json:I,selection:u().getSelection(),deltaY:ee,items:t(b).items});if(P)u().onPatch(P,(V,T)=>({state:T,selection:pe??d()}));else if(Y.target===t(b).initialTarget&&!t(b).didMoveItems){var K=$c(Y.target),Me=Hp(Y.target);Me&&u().onSelect(Pv(K,Me))}v(b,void 0),Vt(Vt().dragging=!1),document.removeEventListener("mousemove",ye,!0),document.removeEventListener("mouseup",te)}}function ve(Y){Y.shiftKey||(Y.stopPropagation(),Y.preventDefault(),u().onSelect(aa(t(j))))}function Re(Y){Y.shiftKey||(Y.stopPropagation(),Y.preventDefault(),u().onSelect(Zr(t(j))))}function Ee(Y){u().onSelect(aa(t(j))),u().onContextMenu(Y)}function fe(Y){u().onSelect(Zr(t(j))),u().onContextMenu(Y)}F(()=>O(s()),()=>{v(j,Sr(s()))}),F(()=>O(s()),()=>{v(o,encodeURIComponent(s()))}),F(()=>O(i()),()=>{v(x,!!Ja(i())&&i().expanded)}),F(()=>(O(a()),O(i())),()=>{v(k,Xr(a(),i(),[]))}),F(()=>O(i()),()=>{v($,To(i())?i().visibleSections:void 0)}),F(()=>O(l()),()=>{var Y;v(S,(Y=l())===null||Y===void 0?void 0:Y.validationError)}),F(()=>(O(u()),O(d()),t(j)),()=>{v(y,ki(u().getJson(),d(),t(j)))}),F(()=>t(j),()=>{v(r,t(j).length===0)}),mt(),jn(!0);var Se,we,Ne=Cx(),de=M(Ne),oe=Y=>{var I=ix(),ee=rn(I),P=M(ee),pe=M(P),K=M(pe),Me=D=>{nt(D,{data:Ca})},V=D=>{nt(D,{data:fi})};Q(K,D=>{t(x)?D(Me):D(V,!1)});var T=q(pe,2);io(T,n,"identifier",{},null);var xn=q(T,2),Qe=D=>{A(D,Qj())};Q(xn,D=>{t(r)||D(Qe)});var cn=q(xn,2),sn=M(cn),je=M(sn),Xe=D=>{var Ke=Gj();ll(q(rn(Ke),2),{children:(Ge,Ct)=>{var _e=zo();be(()=>{var Rn,Un;return ln(_e,"".concat((Rn=a().length)!==null&&Rn!==void 0?Rn:"",`
                `).concat((Un=a().length===1?"item":"items")!==null&&Un!==void 0?Un:""))}),A(Ge,_e)},$$slots:{default:!0}}),A(D,Ke)},$n=D=>{var Ke=Xj();ll(q(rn(Ke),2),{onclick:N,children:(Ge,Ct)=>{var _e=zo();be(()=>{var Rn,Un;return ln(_e,"".concat((Rn=a().length)!==null&&Rn!==void 0?Rn:"",`
                `).concat((Un=a().length===1?"item":"items")!==null&&Un!==void 0?Un:""))}),A(Ge,_e)},$$slots:{default:!0}}),A(D,Ke)};Q(je,D=>{t(x)?D(Xe):D($n,!1)});var De=q(cn,2),Nn=D=>{var Ke=Zj();da(M(Ke),{get root(){return t(r)},selected:!0,get onContextMenu(){return u().onContextMenu}}),A(D,Ke)};Q(De,D=>{!u().readOnly&&t(y)&&d()&&(wt(d())||Ht(d()))&&!Vo(d())&&et(Be(d()),t(j))&&D(Nn)});var ge=q(P,2),qe=D=>{xs(D,{get validationError(){return t(S)},onExpand:N})};Q(ge,D=>{!t(S)||t(x)&&t(S).isChildError||D(qe)});var Ae=q(ge,2),Ue=D=>{var Ke=ex();ie("click",Ke,ve),A(D,Ke)},Fn=D=>{var Ke=nx();ie("click",Ke,Re),A(D,Ke)};Q(Ae,D=>{t(x)?D(Ue):D(Fn,!1)});var gn=q(ee,2),Ve=D=>{var Ke=sx(),Ge=rn(Ke),Ct=M(Ge),_e=ut=>{var _n,Lt,Qn=tx();ht(Qn,"title",Ac);var ft=M(Qn),rt=ae(()=>t(y)&&Ro(d()));da(ft,{insert:!0,get selected(){return t(rt)},onContextMenu:Ee}),be(Ln=>{_n=kn(Qn,1,"jse-insert-area jse-inside svelte-oawf7x",null,_n,Ln),Lt=fr(Qn,"",Lt,{"--level":t(j).length+1})},[()=>({"jse-hovered":t(m)===ei,"jse-selected":t(y)&&Ro(d())})],ae),A(ut,Qn)};Q(Ct,ut=>{!u().readOnly&&(t(m)===ei||t(y)&&Ro(d()))&&ut(_e)}),lo(q(Ct,2),1,()=>t($)||ms,ho,(ut,_n,Lt)=>{var Qn=rx(),ft=rn(Qn);lo(ft,1,()=>function(yn,st,on){var Jn=st.start,Wn=Math.min(st.end,yn.length),Et=Qf(Jn,Wn);return on&&on.offset!==0?hv(Et,on.selectionStartIndex,on.selectionItemsCount,on.offset).map((Kt,bt)=>({index:Kt,gutterIndex:bt})):Et.map(Kt=>({index:Kt,gutterIndex:Kt}))}(a(),t(_n),t(b)),yn=>yn.index,(yn,st)=>{var on=to(),Jn=ae(()=>To(l())?l().items[t(st).index]:void 0),Wn=ae(()=>Iv(u().getJson(),d(),t(j).concat(String(t(st).index)))),Et=rn(on),Kt=ae(()=>Jd(s(),t(st).index)),bt=ae(()=>To(i())?i().items[t(st).index]:void 0),vo=ae(()=>To(c())?c().items[t(st).index]:void 0);zu(Et,{get value(){return a()[t(st).index]},get pointer(){return t(Kt)},get state(){return t(bt)},get validationErrors(){return t(Jn)},get searchResults(){return t(vo)},get selection(){return t(Wn)},get context(){return u()},onDragSelectionStart:ne,$$slots:{identifier:(oo,uo)=>{var jo=ox(),fo=M(jo),Pt=M(fo);be(()=>ln(Pt,t(st).gutterIndex)),A(oo,jo)}}}),A(yn,on)});var rt=q(ft,2),Ln=yn=>{var st=ae(()=>t($)||ms);Jj(yn,{get visibleSections(){return t(st)},sectionIndex:Lt,get total(){return a().length},get path(){return t(j)},get onExpandSection(){return u().onExpandSection},get selection(){return d()},get context(){return u()}})};Q(rt,yn=>{t(_n).end<a().length&&yn(Ln)}),A(ut,Qn)});var Rn=q(Ge,2),Un=q(M(Rn),2),co=ut=>{var _n=ax();ie("click",_n,Re),A(ut,_n)};Q(Un,ut=>{t(r)||ut(co)}),A(D,Ke)};Q(gn,D=>{t(x)&&D(Ve)}),ie("click",pe,R),A(Y,I)},ce=(Y,I)=>{var ee=pe=>{var K=bx(),Me=rn(K),V=M(Me),T=M(V),xn=M(T),Qe=_e=>{nt(_e,{data:Ca})},cn=_e=>{nt(_e,{data:fi})};Q(xn,_e=>{t(x)?_e(Qe):_e(cn,!1)});var sn=q(T,2);io(sn,n,"identifier",{},null);var je=q(sn,2),Xe=_e=>{A(_e,lx())};Q(je,_e=>{t(r)||_e(Xe)});var $n=q(je,2),De=M($n),Nn=M(De),ge=_e=>{A(_e,cx())},qe=_e=>{var Rn=ux();ll(q(rn(Rn),2),{onclick:N,children:(Un,co)=>{var ut=zo();be((_n,Lt)=>ln(ut,"".concat(_n??"",`
                `).concat(Lt??"")),[()=>Object.keys(a()).length,()=>Object.keys(a()).length===1?"prop":"props"],ae),A(Un,ut)},$$slots:{default:!0}}),A(_e,Rn)};Q(Nn,_e=>{t(x)?_e(ge):_e(qe,!1)});var Ae=q($n,2),Ue=_e=>{var Rn=dx();da(M(Rn),{get root(){return t(r)},selected:!0,get onContextMenu(){return u().onContextMenu}}),A(_e,Rn)};Q(Ae,_e=>{!u().readOnly&&t(y)&&d()&&(wt(d())||Ht(d()))&&!Vo(d())&&et(Be(d()),t(j))&&_e(Ue)});var Fn=q(V,2),gn=_e=>{xs(_e,{get validationError(){return t(S)},onExpand:N})};Q(Fn,_e=>{!t(S)||t(x)&&t(S).isChildError||_e(gn)});var Ve=q(Fn,2),D=_e=>{var Rn=vx();ie("click",Rn,ve),A(_e,Rn)},Ke=(_e,Rn)=>{var Un=co=>{var ut=fx();ie("click",ut,Re),A(co,ut)};Q(_e,co=>{t(r)||co(Un)},Rn)};Q(Ve,_e=>{t(x)?_e(D):_e(Ke,!1)});var Ge=q(Me,2),Ct=_e=>{var Rn=mx(),Un=rn(Rn),co=M(Un),ut=ft=>{var rt,Ln,yn=px();ht(yn,"title",Ac);var st=M(yn),on=ae(()=>t(y)&&Ro(d()));da(st,{insert:!0,get selected(){return t(on)},onContextMenu:Ee}),be(Jn=>{rt=kn(yn,1,"jse-insert-area jse-inside svelte-oawf7x",null,rt,Jn),Ln=fr(yn,"",Ln,{"--level":t(j).length+1})},[()=>({"jse-hovered":t(m)===ei,"jse-selected":t(y)&&Ro(d())})],ae),A(ft,yn)};Q(co,ft=>{!u().readOnly&&(t(m)===ei||t(y)&&Ro(d()))&&ft(ut)}),lo(q(co,2),1,()=>function(ft,rt){var Ln=Object.keys(ft);return rt&&rt.offset!==0?hv(Ln,rt.selectionStartIndex,rt.selectionItemsCount,rt.offset):Ln}(a(),t(b)),ho,(ft,rt)=>{var Ln=to(),yn=ae(()=>Jd(s(),t(rt))),st=ae(()=>gr(c())?c().properties[t(rt)]:void 0),on=ae(()=>gr(l())?l().properties[t(rt)]:void 0),Jn=ae(()=>t(j).concat(t(rt))),Wn=ae(()=>Iv(u().getJson(),d(),t(Jn))),Et=rn(Ln),Kt=ae(()=>gr(i())?i().properties[t(rt)]:void 0);zu(Et,{get value(){return a()[t(rt)]},get pointer(){return t(yn)},get state(){return t(Kt)},get validationErrors(){return t(on)},get searchResults(){return t(st)},get selection(){return t(Wn)},get context(){return u()},onDragSelectionStart:ne,$$slots:{identifier:(bt,vo)=>{var oo,uo=hx(),jo=M(uo),fo=ae(()=>{return Pt=t(st),(It=kv(Pt)?Pt.searchResults.filter(Zt=>Zt.field===hr.key):void 0)&&It.length>0?It:void 0;var Pt,It});(function(Pt,It){en(It,!1);var Zt=E(void 0,!0),le=E(void 0,!0),Gn=f(It,"pointer",9),B=f(It,"key",9),ke=f(It,"selection",9),He=f(It,"searchResultItems",9),zn=f(It,"onUpdateKey",9),We=f(It,"context",9),Tn=E(void 0,!0);function dt(Dn){t(le)||We().readOnly||(Dn.preventDefault(),We().onSelect(hd(t(Tn))))}function On(Dn,Pn){var qn=zn()(B(),We().normalization.unescapeValue(Dn)),Jt=ot(t(Tn)).concat(qn);We().onSelect(Pn===wa.nextInside?Yn(Jt):ra(Jt)),Pn!==wa.self&&We().focus()}function pt(){We().onSelect(ra(t(Tn))),We().focus()}F(()=>O(Gn()),()=>{v(Tn,Sr(Gn()))}),F(()=>(O(ke()),t(Tn)),()=>{v(Zt,Co(ke())&&et(ke().path,t(Tn)))}),F(()=>(t(Zt),O(ke())),()=>{v(le,t(Zt)&&Vo(ke()))}),mt(),jn(!0);var un=Vj(),En=rn(un),tn=Dn=>{var Pn=ae(()=>We().normalization.escapeValue(B())),qn=ae(()=>Vo(ke())?ke().initialValue:void 0);rh(Dn,{get value(){return t(Pn)},get initialValue(){return t(qn)},label:"Edit key",shortText:!0,onChange:On,onCancel:pt,get onFind(){return We().onFind}})},pn=Dn=>{var Pn,qn=Wj(),Jt=M(qn),U=he=>{var An=ae(()=>We().normalization.escapeValue(B()));dh(he,{get text(){return t(An)},get searchResultItems(){return He()}})},Z=he=>{var An=zo();be(it=>ln(An,it),[()=>ec(We().normalization.escapeValue(B()))],ae),A(he,An)};Q(Jt,he=>{He()?he(U):he(Z,!1)}),be(he=>Pn=kn(qn,1,"jse-key svelte-2iqnqn",null,Pn,he),[()=>({"jse-empty":B()===""})],ae),ie("dblclick",qn,dt),A(Dn,qn)};Q(En,Dn=>{!We().readOnly&&t(le)?Dn(tn):Dn(pn,!1)});var St=q(En,2),At=Dn=>{da(Dn,{selected:!0,get onContextMenu(){return We().onContextMenu}})};Q(St,Dn=>{We().readOnly||!t(Zt)||t(le)||Dn(At)}),A(Pt,un),nn()})(jo,{get pointer(){return t(yn)},get key(){return t(rt)},get selection(){return t(Wn)},get searchResultItems(){return t(fo)},get context(){return u()},onUpdateKey:_}),be(Pt=>oo=kn(uo,1,"jse-key-outer svelte-oawf7x",null,oo,Pt),[()=>({"jse-selected-key":Co(t(Wn))&&et(t(Wn).path,t(Jn))})],ae),A(bt,uo)}}}),A(ft,Ln)});var _n=q(Un,2),Lt=q(M(_n),2),Qn=ft=>{var rt=gx();ie("click",rt,Re),A(ft,rt)};Q(Lt,ft=>{t(r)||ft(Qn)}),A(_e,Rn)};Q(Ge,_e=>{t(x)&&_e(Ct)}),ie("click",T,R),A(pe,K)},P=pe=>{var K=wx(),Me=M(K),V=M(Me);io(V,n,"identifier",{},null);var T=q(V,2),xn=Ae=>{A(Ae,jx())};Q(T,Ae=>{t(r)||Ae(xn)});var Qe=q(T,2),cn=M(Qe),sn=ae(()=>t(y)?d():void 0),je=ae(()=>{return Ae=c(),(Ue=kv(Ae)?Ae.searchResults.filter(Fn=>Fn.field===hr.value):void 0)&&Ue.length>0?Ue:void 0;var Ae,Ue});Oh(cn,{get path(){return t(j)},get value(){return a()},get enforceString(){return t(k)},get selection(){return t(sn)},get searchResultItems(){return t(je)},get context(){return u()}});var Xe=q(Qe,2),$n=Ae=>{var Ue=xx();da(M(Ue),{get root(){return t(r)},selected:!0,get onContextMenu(){return u().onContextMenu}}),A(Ae,Ue)};Q(Xe,Ae=>{!u().readOnly&&t(y)&&d()&&(wt(d())||Ht(d()))&&!Vo(d())&&et(Be(d()),t(j))&&Ae($n)});var De=q(Me,2),Nn=Ae=>{xs(Ae,{get validationError(){return t(S)},onExpand:N})};Q(De,Ae=>{t(S)&&Ae(Nn)});var ge=q(De,2),qe=Ae=>{var Ue=yx();ie("click",Ue,Re),A(Ae,Ue)};Q(ge,Ae=>{t(r)||Ae(qe)}),A(pe,K)};Q(Y,pe=>{gt(a())?pe(ee):pe(P,!1)},I)};Q(de,Y=>{Array.isArray(a())?Y(oe):Y(ce,!1)});var an=q(de,2),Mn=Y=>{var I,ee=kx();ht(ee,"title",Ac);var P=M(ee),pe=ae(()=>t(y)&&or(d()));da(P,{insert:!0,get selected(){return t(pe)},onContextMenu:fe}),be(K=>I=kn(ee,1,"jse-insert-area jse-after svelte-oawf7x",null,I,K),[()=>({"jse-hovered":t(m)===Rc,"jse-selected":t(y)&&or(d())})],ae),A(Y,ee)};Q(an,Y=>{!u().readOnly&&(t(m)===Rc||t(y)&&or(d()))&&Y(Mn)}),be((Y,I)=>{Se=kn(Ne,1,Y,"svelte-oawf7x",Se,I),ht(Ne,"data-path",t(o)),ht(Ne,"aria-selected",t(y)),we=fr(Ne,"",we,{"--level":t(j).length})},[()=>za(Fs("jse-json-node",{"jse-expanded":t(x)},u().onClassName(t(j),a()))),()=>({"jse-root":t(r),"jse-selected":t(y)&&Ht(d()),"jse-selected-value":t(y)&&wt(d()),"jse-readonly":u().readOnly,"jse-hovered":t(m)===jv})],ae),ie("mousedown",Ne,function(Y){if((Y.buttons===1||Y.buttons===2)&&!((I=Y.target).nodeName==="DIV"&&I.contentEditable==="true"||Y.buttons===1&&Wp(Y.target,"BUTTON"))){var I;Y.stopPropagation(),Y.preventDefault(),u().focus(),document.addEventListener("mousemove",z,!0),document.addEventListener("mouseup",W);var ee=$c(Y.target),P=u().getJson(),pe=u().getDocumentState();if(!d()||ee===$t.after||ee===$t.inside||d().type!==ee&&d().type!==$t.multi||!ki(P,d(),t(j)))if(Vt(Vt().selecting=!0),Vt(Vt().selectionAnchor=t(j)),Vt(Vt().selectionAnchorType=ee),Vt(Vt().selectionFocus=t(j)),Y.shiftKey){var K=u().getSelection();K&&u().onSelect(qo(qa(K),t(j)))}else if(ee===$t.multi)if(t(r)&&Y.target.hasAttribute("data-path")){var Me=Kn(Zp(a(),pe));u().onSelect(mu(Me))}else u().onSelect(qo(t(j),t(j)));else P!==void 0&&u().onSelect(Pv(ee,t(j)));else Y.button===0&&h()(Y)}}),ie("mousemove",Ne,function(Y){if(Vt().selecting){Y.preventDefault(),Y.stopPropagation(),Vt().selectionFocus===void 0&&window.getSelection&&window.getSelection().empty();var I=$c(Y.target);et(t(j),Vt().selectionFocus)&&I===Vt().selectionAnchorType||(Vt(Vt().selectionFocus=t(j)),Vt(Vt().selectionAnchorType=I),u().onSelect(qo(Vt().selectionAnchor||Vt().selectionFocus,Vt().selectionFocus)))}}),ie("mouseover",Ne,function(Y){Vt().selecting||Vt().dragging||(Y.stopPropagation(),ma(Y.target,"data-type","selectable-value")?v(m,jv):ma(Y.target,"data-type","selectable-key")?v(m,void 0):ma(Y.target,"data-type","insert-selection-area-inside")?v(m,ei):ma(Y.target,"data-type","insert-selection-area-after")&&v(m,Rc),clearTimeout(g))}),ie("mouseout",Ne,function(Y){Y.stopPropagation(),g=window.setTimeout(()=>v(m,void 0))}),A(e,Ne),nn()}var Sx={prefix:"fas",iconName:"jsoneditor-expand",icon:[512,512,[],"","M 0,448 V 512 h 512 v -64 z M 0,0 V 64 H 512 V 0 Z M 256,96 128,224 h 256 z M 256,416 384,288 H 128 Z"]},zx={prefix:"fas",iconName:"jsoneditor-collapse",icon:[512,512,[],"","m 0,224 v 64 h 512 v -64 z M 256,192 384,64 H 128 Z M 256,320 128,448 h 256 z"]},Qv={prefix:"fas",iconName:"jsoneditor-format",icon:[512,512,[],"","M 0,32 v 64 h 416 v -64 z M 160,160 v 64 h 352 v -64 z M 160,288 v 64 h 288 v -64 z M 0,416 v 64 h 320 v -64 z"]},Ox={prefix:"fas",iconName:"jsoneditor-compact",icon:[512,512,[],"","M 0,32 v 64 h 512 v -64 z M 0,160 v 64 h 512 v -64 z M 0,288 v 64 h 352 v -64 z"]};function Mx(e,n){e.stopPropagation(),n.onCreateObject()}function $x(e,n){e.stopPropagation(),n.onCreateArray()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-welcome.svelte-1eamlhk {
  flex: 1;
  overflow: auto;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  display: flex;
  flex-direction: column;
  align-items: center;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-welcome.svelte-1eamlhk:last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-welcome.svelte-1eamlhk .jse-space.jse-before:where(.svelte-1eamlhk) {
  flex: 1;
}
.jse-welcome.svelte-1eamlhk .jse-space.jse-after:where(.svelte-1eamlhk) {
  flex: 2;
}
.jse-welcome.svelte-1eamlhk .jse-contents:where(.svelte-1eamlhk) {
  display: flex;
  flex-direction: column;
  max-width: 300px;
  margin: 2em var(--jse-padding, 10px);
  gap: var(--jse-padding, 10px);
}
.jse-welcome.svelte-1eamlhk .jse-contents:where(.svelte-1eamlhk) .jse-welcome-info:where(.svelte-1eamlhk) {
  color: var(--jse-panel-color-readonly, #b2b2b2);
}
.jse-welcome.svelte-1eamlhk .jse-contents:where(.svelte-1eamlhk) button:where(.svelte-1eamlhk) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-welcome.svelte-1eamlhk .jse-contents:where(.svelte-1eamlhk) button:where(.svelte-1eamlhk):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-welcome.svelte-1eamlhk .jse-contents:where(.svelte-1eamlhk) button:where(.svelte-1eamlhk):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}`);var Ex=(e,n)=>n.onClick(),Ax=L('<div class="jse-welcome-info svelte-1eamlhk">You can paste clipboard data using <b>Ctrl+V</b>, or use the following options:</div> <button class="svelte-1eamlhk">Create object</button> <button class="svelte-1eamlhk">Create array</button>',1),Rx=L('<div class="jse-welcome svelte-1eamlhk" role="none"><div class="jse-space jse-before svelte-1eamlhk"></div> <div class="jse-contents svelte-1eamlhk"><div class="jse-welcome-title">Empty document</div> <!></div> <div class="jse-space jse-after svelte-1eamlhk"></div></div>');function Ou(e,n){var o=typeof e=="string"?e.toLowerCase():e,r=typeof n=="string"?n.toLowerCase():n;return dm(o,r)}function Mh(e){var n=arguments.length>1&&arguments[1]!==void 0?arguments[1]:[],o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:[],r=arguments.length>3&&arguments[3]!==void 0?arguments[3]:1,s=Je(e,n);if(wo(s)){if(o===void 0)throw new Error("Cannot sort: no property selected by which to sort the array");return function(a){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:[],l=arguments.length>2&&arguments[2]!==void 0?arguments[2]:[],c=arguments.length>3&&arguments[3]!==void 0?arguments[3]:1,d=function(h,p){return function(m,g){var b=Je(m,h),j=Je(g,h);return b===void 0?p:j===void 0?-p:typeof b!="string"&&typeof j!="string"?b>j?p:b<j?-p:0:p*Ou(b,j)}}(l,c),u=Je(a,i);return[{op:"replace",path:Sn(i),value:u.slice(0).sort(d)}]}(e,n,o,r)}if(gt(s))return function(a){var i=arguments.length>1&&arguments[1]!==void 0?arguments[1]:[],l=arguments.length>2&&arguments[2]!==void 0?arguments[2]:1,c=Je(a,i),d=Object.keys(c).slice();d.sort((h,p)=>l*Ou(h,p));var u={};return d.forEach(h=>u[h]=c[h]),[{op:"replace",path:Sn(i),value:u}]}(e,n,r);throw new Error("Cannot sort: no array or object")}Ii(["click"]);fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-navigation-bar-dropdown.svelte-2nnd2m {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 3;
  background: var(--jse-navigation-bar-background, var(--jse-background-color, #fff));
  color: var(--jse-navigation-bar-dropdown-color, #656565);
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
  display: flex;
  flex-direction: column;
  max-height: 300px;
  overflow: auto;
  min-width: 80px;
}
.jse-navigation-bar-dropdown.svelte-2nnd2m button.jse-navigation-bar-dropdown-item:where(.svelte-2nnd2m) {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  outline: none;
  text-align: left;
  white-space: nowrap;
  box-sizing: border-box;
  padding: calc(0.5 * var(--jse-padding, 10px)) 36px;
}
.jse-navigation-bar-dropdown.svelte-2nnd2m button.jse-navigation-bar-dropdown-item:where(.svelte-2nnd2m):focus, .jse-navigation-bar-dropdown.svelte-2nnd2m button.jse-navigation-bar-dropdown-item:where(.svelte-2nnd2m):hover {
  background: var(--jse-navigation-bar-background-highlight, #e5e5e5);
}
.jse-navigation-bar-dropdown.svelte-2nnd2m button.jse-navigation-bar-dropdown-item.jse-selected:where(.svelte-2nnd2m) {
  background: var(--jse-navigation-bar-dropdown-color, #656565);
  color: var(--jse-navigation-bar-background, var(--jse-background-color, #fff));
}`);var Tx=L('<button type="button"> </button>'),Px=L('<button type="button" class="jse-navigation-bar-dropdown-item svelte-2nnd2m">...</button>'),Ix=L('<div class="jse-navigation-bar-dropdown svelte-2nnd2m"><!> <!></div>');function qx(e,n){en(n,!1);var o=f(n,"items",9),r=f(n,"selectedItem",9),s=f(n,"onSelect",9);jn(!0);var a=Ix(),i=M(a);lo(i,1,()=>qp(o(),100),d=>d,(d,u)=>{var h,p=Tx(),m=M(p);be((g,b,j)=>{h=kn(p,1,"jse-navigation-bar-dropdown-item svelte-2nnd2m",null,h,g),ht(p,"title",b),ln(m,j)},[()=>({"jse-selected":t(u)===r()}),()=>t(u).toString(),()=>bi(t(u).toString(),30)],ae),ie("click",p,Jr(()=>s()(t(u)))),A(d,p)});var l=q(i,2),c=d=>{var u=Px();ht(u,"title","Limited to ".concat(100," items")),A(d,u)};Q(l,d=>{o().length>100&&d(c)}),A(e,a),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-navigation-bar-item.svelte-752ro1 {
  position: relative;
  display: flex;
}
.jse-navigation-bar-item.svelte-752ro1 button.jse-navigation-bar-button:where(.svelte-752ro1) {
  font-family: inherit;
  font-size: inherit;
  padding: calc(0.5 * var(--jse-padding, 10px)) 2px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  outline: none;
  min-width: 2em;
  white-space: nowrap;
}
.jse-navigation-bar-item.svelte-752ro1 button.jse-navigation-bar-button:where(.svelte-752ro1):focus, .jse-navigation-bar-item.svelte-752ro1 button.jse-navigation-bar-button:where(.svelte-752ro1):hover {
  background: var(--jse-panel-button-background-highlight, #e0e0e0);
  color: var(--panel-button-color-highlight, var(--jse-text-color, #4d4d4d));
}
.jse-navigation-bar-item.svelte-752ro1 button.jse-navigation-bar-button.jse-navigation-bar-arrow:where(.svelte-752ro1) {
  padding: 2px var(--jse-padding, 10px) 0;
}
.jse-navigation-bar-item.svelte-752ro1 button.jse-navigation-bar-button.jse-navigation-bar-arrow.jse-open:where(.svelte-752ro1) {
  background: var(--jse-navigation-bar-background, var(--jse-background-color, #fff));
  color: var(--jse-navigation-bar-dropdown-color, #656565);
}
.jse-navigation-bar-item.svelte-752ro1:last-child {
  padding-right: var(--jse-padding, 10px);
}`);var Nx=L('<button type="button" class="jse-navigation-bar-button svelte-752ro1"> </button>'),Ux=L('<div class="jse-navigation-bar-item svelte-752ro1"><button type="button"><!></button> <!></div>');function Gv(e,n){en(n,!1);var o,r=E(void 0,!0),s=E(void 0,!0),{openAbsolutePopup:a,closeAbsolutePopup:i}=Ma("absolute-popup"),l=f(n,"path",9),c=f(n,"index",9),d=f(n,"onSelect",9),u=f(n,"getItems",9),h=E(void 0,!0),p=E(!1,!0);function m($){i(o),d()(t(r).concat($))}F(()=>(O(l()),O(c())),()=>{v(r,l().slice(0,c()))}),F(()=>(O(l()),O(c())),()=>{v(s,l()[c()])}),mt(),jn(!0);var g,b=Ux(),j=M(b);nt(M(j),{data:Kf});var x=q(j,2),k=$=>{var S=Nx(),y=M(S);be(()=>ln(y,t(s))),ie("click",S,()=>m(t(s))),A($,S)};Q(x,$=>{t(s)!==void 0&&$(k)}),Yt(b,$=>v(h,$),()=>t(h)),be($=>g=kn(j,1,"jse-navigation-bar-button jse-navigation-bar-arrow svelte-752ro1",null,g,$),[()=>({"jse-open":t(p)})],ae),ie("click",j,function(){if(t(h)){v(p,!0);var $={items:u()(t(r)),selectedItem:t(s),onSelect:m};o=a(qx,$,{anchor:t(h),closeOnOuterClick:!0,onClose:()=>{v(p,!1)}})}}),A(e,b),nn()}function yd(e){var n,o;if(navigator.clipboard)return navigator.clipboard.writeText(e);if((n=(o=document).queryCommandSupported)!==null&&n!==void 0&&n.call(o,"copy")){var r=document.createElement("textarea");r.value=e,r.style.position="fixed",r.style.opacity="0",document.body.appendChild(r),r.select();try{document.execCommand("copy")}catch(s){console.error(s)}finally{document.body.removeChild(r)}return Promise.resolve()}return console.error("Copy failed."),Promise.resolve()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-navigation-bar-path-editor.svelte-zc2wx7 {
  flex: 1;
  display: flex;
  border: var(--jse-edit-outline, 2px solid #656565);
  background: var(--jse-background-color, #fff);
}
.jse-navigation-bar-path-editor.svelte-zc2wx7 input.jse-navigation-bar-text:where(.svelte-zc2wx7) {
  flex: 1;
  font-family: inherit;
  font-size: inherit;
  padding: 0 5px 1px;
  background: var(--jse-background-color, #fff);
  color: var(--jse-text-color, #4d4d4d);
  border: none;
  outline: none;
}
.jse-navigation-bar-path-editor.svelte-zc2wx7 button:where(.svelte-zc2wx7) {
  border: none;
  background: var(--jse-background-color, #fff);
  cursor: pointer;
  font-family: inherit;
  font-size: 80%;
  color: inherit;
}
.jse-navigation-bar-path-editor.svelte-zc2wx7 button.jse-navigation-bar-copy.copied:where(.svelte-zc2wx7) {
  color: var(--message-success-background, #9ac45d);
}
.jse-navigation-bar-path-editor.svelte-zc2wx7 button.jse-navigation-bar-validation-error:where(.svelte-zc2wx7) {
  color: var(--jse-error-color, #ee5341);
}
.jse-navigation-bar-path-editor.error.svelte-zc2wx7 {
  border-color: var(--jse-error-color, #ee5341);
}
.jse-navigation-bar-path-editor.error.svelte-zc2wx7 input.jse-navigation-bar-text:where(.svelte-zc2wx7) {
  color: var(--jse-error-color, #ee5341);
}
.jse-navigation-bar-path-editor.svelte-zc2wx7 .jse-copied-text:where(.svelte-zc2wx7) {
  background: var(--message-success-background, #9ac45d);
  color: var(--jse-message-success-color, #fff);
  position: relative;
  margin: 2px;
  padding: 0 5px;
  border-radius: 3px;
}`);var _x=L('<button type="button" class="jse-navigation-bar-validation-error svelte-zc2wx7"><!></button>'),Dx=L('<div class="jse-copied-text svelte-zc2wx7">Copied!</div>'),Fx=L('<div><input type="text" class="jse-navigation-bar-text svelte-zc2wx7"> <!> <!> <button type="button" title="Copy selected path to the clipboard"><!></button></div>');function Lx(e,n){en(n,!1);var o=E(),r=Ma("absolute-popup"),s=f(n,"path",8),a=f(n,"pathParser",8),i=f(n,"onChange",8),l=f(n,"onClose",8),c=f(n,"onError",8),d=f(n,"pathExists",8),u=E(),h=E(),p=E(!1),m=void 0,g=E(!1);function b(){t(u).focus()}function j(W){try{var X=a().parse(W);return function(H){if(!d()(H))throw new Error("Path does not exist in current document")}(X),{path:X,error:void 0}}catch(H){return{path:void 0,error:H}}}Po(()=>{b()}),rr(()=>{clearTimeout(m)}),F(()=>(O(a()),O(s())),()=>{v(h,a().stringify(s()))}),F(()=>(t(p),t(h)),()=>{v(o,t(p)?j(t(h)).error:void 0)}),mt(),jn();var x,k=Fx(),$=M(k);Yt($,W=>v(u,W),()=>t(u));var S=q($,2),y=W=>{var X=_x();nt(M(X),{data:_a}),Uo(X,(H,ne)=>Ms==null?void 0:Ms(H,ne),()=>me({text:String(t(o)||"")},r)),A(W,X)};Q(S,W=>{t(o)&&W(y)});var R=q(S,2),N=W=>{A(W,Dx())};Q(R,W=>{t(g)&&W(N)});var _,z=q(R,2);nt(M(z),{data:ha}),be((W,X)=>{x=kn(k,1,"jse-navigation-bar-path-editor svelte-zc2wx7",null,x,W),La($,t(h)),_=kn(z,1,"jse-navigation-bar-copy svelte-zc2wx7",null,_,X)},[()=>({error:t(o)}),()=>({copied:t(g)})],ae),ie("keydown",$,Jr(function(W){var X=ta(W);if(X==="Escape"&&(W.preventDefault(),l()()),X==="Enter"){W.preventDefault(),v(p,!0);var H=j(t(h));H.path!==void 0?i()(H.path):c()(H.error)}})),ie("input",$,function(W){v(h,W.currentTarget.value)}),ie("click",z,function(){yd(t(h)),v(g,!0),m=window.setTimeout(()=>v(g,!1),1e3),b()}),A(e,k),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-navigation-bar.svelte-xs03gj {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  background: var(--jse-panel-background, #ebebeb);
  color: var(--jse-panel-button-color, inherit);
  padding: 0;
  margin: 0;
  display: flex;
  overflow: auto;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit:where(.svelte-xs03gj) {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  background: transparent;
  border: none;
  display: flex;
  cursor: pointer;
  outline: none;
  align-items: center;
}
.jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit.flex:where(.svelte-xs03gj) {
  flex: 1;
}
.jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit:where(.svelte-xs03gj):focus, .jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit:where(.svelte-xs03gj):hover, .jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit.editing:where(.svelte-xs03gj) {
  background: var(--jse-panel-button-background-highlight, #e0e0e0);
  color: var(--panel-button-color-highlight, var(--jse-text-color, #4d4d4d));
  transition: color 0.2s ease-in, background 0.2s ease-in;
}
.jse-navigation-bar.svelte-xs03gj .jse-navigation-bar-edit:where(.svelte-xs03gj) .jse-navigation-bar-space:where(.svelte-xs03gj) {
  flex: 1;
  text-align: left;
}`);var Jx=L("<!> <!>",1),Bx=L('<div class="jse-navigation-bar svelte-xs03gj"><!> <button type="button"><span class="jse-navigation-bar-space svelte-xs03gj"> </span> <!></button></div>');function Wx(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=So("jsoneditor:NavigationBar"),a=f(n,"json",9),i=f(n,"selection",9),l=f(n,"onSelect",9),c=f(n,"onError",9),d=f(n,"pathParser",9),u=E(void 0,!0),h=E(!1,!0);function p(X){s("get items for path",X);var H=Je(a(),X);if(Array.isArray(H))return Qf(0,H.length).map(String);if(gt(H)){var ne=Object.keys(H).slice(0);return ne.sort(Ou),ne}return[]}function m(X){return pa(a(),X)}function g(X){s("select path",JSON.stringify(X)),l()(qo(X,X))}function b(){v(h,!1)}function j(X){b(),g(X)}F(()=>(O(i()),Be),()=>{v(o,i()?Be(i()):[])}),F(()=>(O(a()),t(o)),()=>{v(r,po(Je(a(),t(o))))}),F(()=>t(o),()=>{t(o),setTimeout(()=>{if(t(u)&&t(u).scrollTo){var X=t(u).scrollWidth-t(u).clientWidth;X>0&&(s("scrollTo ",X),t(u).scrollTo({left:X,behavior:"smooth"}))}})}),mt(),jn(!0);var x=Bx(),k=M(x),$=X=>{var H=Jx(),ne=rn(H);lo(ne,1,()=>t(o),ho,(ve,Re,Ee)=>{Gv(ve,{getItems:p,get path(){return t(o)},index:Ee,onSelect:g})});var ye=q(ne,2),te=ve=>{Gv(ve,{getItems:p,get path(){return t(o)},get index(){return t(o).length},onSelect:g})};Q(ye,ve=>{t(r)&&ve(te)}),A(X,H)},S=X=>{Lx(X,{get path(){return t(o)},onClose:b,onChange:j,get onError(){return c()},pathExists:m,get pathParser(){return d()}})};Q(k,X=>{t(h)?X(S,!1):X($)});var y,R=q(k,2),N=M(R),_=M(N),z=q(N,2),W=ae(()=>t(h)?wm:km);nt(z,{get data(){return t(W)}}),Yt(x,X=>v(u,X),()=>t(u)),be((X,H)=>{y=kn(R,1,"jse-navigation-bar-edit svelte-xs03gj",null,y,X),ht(R,"title",t(h)?"Cancel editing the selected path":"Edit the selected path"),ln(_,H)},[()=>({flex:!t(h),editing:t(h)}),()=>po(a())||t(h)?" ":"Navigation bar"],ae),ie("click",R,function(){v(h,!t(h))}),A(e,x),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-search-box.svelte-1mxl2uo {
  border: var(--jse-panel-border, var(--jse-main-border, 1px solid #d7d7d7));
  border-radius: 3px;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  background: var(--jse-panel-background, #ebebeb);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
  display: inline-block;
  width: 400px;
  max-width: 100%;
  overflow: auto;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) {
  display: flex;
  align-items: stretch;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) button:where(.svelte-1mxl2uo),
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) input:where(.svelte-1mxl2uo) {
  font-family: inherit;
  font-size: inherit;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) button:where(.svelte-1mxl2uo) {
  display: block;
  text-align: center;
  border: none;
  padding: 0 5px;
  margin: 0;
  cursor: pointer;
  color: var(--jse-panel-button-color, inherit);
  background: var(--jse-panel-button-background, transparent);
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) button:where(.svelte-1mxl2uo):hover {
  color: var(--panel-button-color-highlight, var(--jse-text-color, #4d4d4d));
  background: var(--jse-panel-button-background-highlight, #e0e0e0);
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) input:where(.svelte-1mxl2uo) {
  color: var(--jse-panel-color, var(--jse-text-color, #4d4d4d));
  border: var(--jse-input-border, 1px solid #d8dbdf);
  border-radius: 3px;
  background: var(--jse-input-background, var(--jse-background-color, #fff));
  height: 28px;
  padding: 0 5px;
  margin: 0;
  flex: 1;
  width: 0;
  min-width: 50px;
  outline: none;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-replace-toggle:where(.svelte-1mxl2uo) {
  padding: var(--jse-padding, 10px) calc(0.5 * var(--jse-padding, 10px));
  min-width: 20px;
  background: var(--jse-panel-button-background-highlight, #e0e0e0);
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: calc(0.5 * var(--jse-padding, 10px));
  gap: calc(0.5 * var(--jse-padding, 10px));
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-search-section:where(.svelte-1mxl2uo) {
  flex: 1;
  display: flex;
  align-items: center;
  position: relative;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-search-section:where(.svelte-1mxl2uo) .jse-search-icon:where(.svelte-1mxl2uo) {
  color: inherit;
  cursor: inherit;
  background: inherit;
  width: 32px;
  text-align: center;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-search-section:where(.svelte-1mxl2uo) label.jse-search-input-label:where(.svelte-1mxl2uo) {
  flex: 1;
  display: flex;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-search-section:where(.svelte-1mxl2uo) .jse-search-count:where(.svelte-1mxl2uo) {
  color: inherit;
  font-size: 80%;
  visibility: hidden;
  padding: 0 5px;
  min-width: 36px;
  text-align: center;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-search-section:where(.svelte-1mxl2uo) .jse-search-count.jse-visible:where(.svelte-1mxl2uo) {
  visibility: visible;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-replace-section:where(.svelte-1mxl2uo) {
  flex: 1;
  display: flex;
  padding-left: 32px;
}
.jse-search-box.svelte-1mxl2uo .jse-search-form:where(.svelte-1mxl2uo) .jse-search-contents:where(.svelte-1mxl2uo) .jse-replace-section:where(.svelte-1mxl2uo) button:where(.svelte-1mxl2uo) {
  width: auto;
}`);var Vx=L('<button type="button" class="jse-replace-toggle svelte-1mxl2uo" title="Toggle visibility of replace options (Ctrl+H)"><!></button>'),Hx=L('<div class="jse-replace-section svelte-1mxl2uo"><input class="jse-replace-input svelte-1mxl2uo" title="Enter replacement text" type="text" placeholder="Replace"> <button type="button" title="Replace current occurrence (Ctrl+Enter)" class="svelte-1mxl2uo">Replace</button> <button type="button" title="Replace all occurrences" class="svelte-1mxl2uo">All</button></div>'),Yx=L('<div class="jse-search-box svelte-1mxl2uo"><form class="jse-search-form svelte-1mxl2uo"><!> <div class="jse-search-contents svelte-1mxl2uo"><div class="jse-search-section svelte-1mxl2uo"><div class="jse-search-icon svelte-1mxl2uo"><!></div> <label class="jse-search-input-label svelte-1mxl2uo" about="jse-search input"><input class="jse-search-input svelte-1mxl2uo" title="Enter text to search" type="text" placeholder="Find"></label> <div> </div> <button type="button" class="jse-search-next svelte-1mxl2uo" title="Go to next search result (Enter)"><!></button> <button type="button" class="jse-search-previous svelte-1mxl2uo" title="Go to previous search result (Shift+Enter)"><!></button> <button type="button" class="jse-search-clear svelte-1mxl2uo" title="Close search box (Esc)"><!></button></div> <!></div></form></div>');function $h(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=E(void 0,!0),a=So("jsoneditor:SearchBox"),i=f(n,"json",9),l=f(n,"documentState",9),c=f(n,"parser",9),d=f(n,"showSearch",9),u=f(n,"showReplace",13),h=f(n,"readOnly",9),p=f(n,"columns",9),m=f(n,"onSearch",9),g=f(n,"onFocus",9),b=f(n,"onPatch",9),j=f(n,"onClose",9),x=E("",!0),k="",$=E("",!0),S=E(!1,!0),y=E(void 0,!0),R=fl(function(K){return ce.apply(this,arguments)},300),N=fl(function(K){return an.apply(this,arguments)},300);function _(){u(!u()&&!h())}function z(K){K.stopPropagation();var Me=ta(K);Me==="Enter"&&(K.preventDefault(),t(x)!==k?R.flush():Ee()),Me==="Shift+Enter"&&(K.preventDefault(),Se()),Me==="Ctrl+Enter"&&(K.preventDefault(),u()?ne():Ee()),Me==="Ctrl+H"&&(K.preventDefault(),_()),Me==="Escape"&&(K.preventDefault(),I())}function W(K){ta(K)==="Enter"&&(K.preventDefault(),K.stopPropagation(),ne())}function X(){return H.apply(this,arguments)}function H(){return(H=bn(function*(){Gt(),yield R.flush()})).apply(this,arguments)}function ne(){return ye.apply(this,arguments)}function ye(){return(ye=bn(function*(){var K;if(!h()){var Me=(K=t(y))===null||K===void 0?void 0:K.activeItem;if(a("handleReplace",{replaceText:t($),activeItem:Me}),t(y)&&Me&&i()!==void 0){v(y,me(me({},qv(t(y))),{},{activeIndex:t(r)}));var{operations:V,newSelection:T}=j0(i(),l(),t($),Me,c());b()(V,(xn,Qe)=>({state:Qe,selection:T})),Gt(),yield N.flush(),yield Ne()}}})).apply(this,arguments)}function te(){return ve.apply(this,arguments)}function ve(){return(ve=bn(function*(){if(!h()){a("handleReplaceAll",{text:t(x),replaceText:t($)});var{operations:K,newSelection:Me}=function(V,T,xn,Qe,cn){for(var sn=Nv(xn,V,{maxResults:1/0}),je=[],Xe=0;Xe<sn.length;Xe++){var $n=sn[Xe-1],De=sn[Xe];Xe!==0&&De.field===$n.field&&et(De.path,$n.path)?Kn(je).items.push(De):je.push({path:De.path,field:De.field,items:[De]})}je.sort((qe,Ae)=>qe.field!==Ae.field?qe.field===hr.key?1:-1:Ae.path.length-qe.path.length);var Nn,ge=[];return je.forEach(qe=>{var{field:Ae,path:Ue,items:Fn}=qe;if(Ae===hr.key){var gn=ot(Ue),Ve=Je(V,gn),D=Kn(Ue),Ke=Di(gn,Object.keys(Ve),D,_v(D,Qe,Fn));ge=ge.concat(Ke),Nn=Os(V,Ke)}else{if(Ae!==hr.value)throw new Error("Cannot replace: unknown type of search result field ".concat(Ae));var Ge=Je(V,Ue);if(Ge===void 0)throw new Error("Cannot replace: path not found ".concat(Sn(Ue)));var Ct=typeof Ge=="string"?Ge:String(Ge),_e=Xr(V,T,Ue),Rn=_v(Ct,Qe,Fn),Un=[{op:"replace",path:Sn(Ue),value:_e?Rn:_s(Rn,cn)}];ge=ge.concat(Un),Nn=Os(V,Un)}}),{operations:ge,newSelection:Nn}}(i(),l(),t(x),t($),c());b()(K,(V,T)=>({state:T,selection:Me})),yield Ne()}})).apply(this,arguments)}function Re(K){K.select()}function Ee(){return fe.apply(this,arguments)}function fe(){return(fe=bn(function*(){v(y,t(y)?qv(t(y)):void 0),yield Ne()})).apply(this,arguments)}function Se(){return we.apply(this,arguments)}function we(){return we=bn(function*(){v(y,t(y)?function(K){var Me=K.activeIndex>0?K.activeIndex-1:K.items.length-1,V=K.items[Me],T=K.items.map((xn,Qe)=>me(me({},xn),{},{active:Qe===Me}));return me(me({},K),{},{items:T,activeItem:V,activeIndex:Me})}(t(y)):void 0),yield Ne()}),we.apply(this,arguments)}function Ne(){return de.apply(this,arguments)}function de(){return(de=bn(function*(){var K;a("handleFocus",t(y));var Me=(K=t(y))===null||K===void 0?void 0:K.activeItem;Me&&i()!==void 0&&(yield g()(Me.path,Me.resultIndex))})).apply(this,arguments)}function oe(){return oe=bn(function*(K){yield Mn(K,t(x),i())}),oe.apply(this,arguments)}function ce(){return ce=bn(function*(K){yield Mn(d(),K,i()),yield Ne()}),ce.apply(this,arguments)}function an(){return an=bn(function*(K){yield Mn(d(),t(x),K)}),an.apply(this,arguments)}function Mn(K,Me,V){return Y.apply(this,arguments)}function Y(){return Y=bn(function*(K,Me,V){return K?(a("applySearch",{showSearch:K,text:Me}),Me===""?(a("clearing search result"),t(y)!==void 0&&v(y,void 0),Promise.resolve()):(k=Me,v(S,!0),new Promise(T=>{setTimeout(()=>{var xn=Nv(Me,V,{maxResults:Ec,columns:p()});v(y,function(Qe,cn){var sn=cn!=null&&cn.activeItem?Dv(cn.activeItem):void 0,je=Qe.findIndex(De=>et(sn,Dv(De))),Xe=je!==-1?je:(cn==null?void 0:cn.activeIndex)!==void 0&&(cn==null?void 0:cn.activeIndex)<Qe.length?cn==null?void 0:cn.activeIndex:Qe.length>0?0:-1,$n=Qe.map((De,Nn)=>me(me({resultIndex:Nn},De),{},{active:Nn===Xe}));return{items:$n,activeItem:$n[Xe],activeIndex:Xe}}(xn,t(y))),v(S,!1),T()})}))):(t(y)&&v(y,void 0),Promise.resolve())}),Y.apply(this,arguments)}function I(){a("handleClose"),R.cancel(),N.cancel(),Mn(!1,t(x),i()),j()()}F(()=>t(y),()=>{var K;v(o,((K=t(y))===null||K===void 0||(K=K.items)===null||K===void 0?void 0:K.length)||0)}),F(()=>t(y),()=>{var K;v(r,((K=t(y))===null||K===void 0?void 0:K.activeIndex)||0)}),F(()=>(t(o),Ec),()=>{v(s,t(o)>=Ec?"".concat(999,"+"):String(t(o)))}),F(()=>(O(m()),t(y)),()=>{m()(t(y))}),F(()=>O(d()),()=>{(function(K){oe.apply(this,arguments)})(d())}),F(()=>t(x),()=>{R(t(x))}),F(()=>O(i()),()=>{N(i())}),mt(),jn(!0);var ee=to(),P=rn(ee),pe=K=>{var Me=Yx(),V=M(Me),T=M(V),xn=D=>{var Ke=Vx(),Ge=M(Ke),Ct=ae(()=>u()?Ca:fi);nt(Ge,{get data(){return t(Ct)}}),ie("click",Ke,_),A(D,Ke)};Q(T,D=>{h()||D(xn)});var Qe=M(q(T,2)),cn=M(Qe),sn=M(cn),je=D=>{nt(D,{data:Tm,spin:!0})},Xe=D=>{nt(D,{data:Jl})};Q(sn,D=>{t(S)?D(je):D(Xe,!1)});var $n=q(cn,2),De=M($n);$o(()=>kl(De,()=>t(x),D=>v(x,D))),Uo(De,D=>Re==null?void 0:Re(D)),$o(()=>ie("paste",De,X));var Nn,ge=q($n,2),qe=M(ge),Ae=q(ge,2);nt(M(Ae),{data:Em});var Ue=q(Ae,2);nt(M(Ue),{data:Am});var Fn=q(Ue,2);nt(M(Fn),{data:Ll});var gn=q(Qe,2),Ve=D=>{var Ke=Hx(),Ge=M(Ke),Ct=q(Ge,2),_e=q(Ct,2);kl(Ge,()=>t($),Rn=>v($,Rn)),ie("keydown",Ge,W),ie("click",Ct,ne),ie("click",_e,te),A(D,Ke)};Q(gn,D=>{u()&&!h()&&D(Ve)}),be(D=>{var Ke,Ge;Nn=kn(ge,1,"jse-search-count svelte-1mxl2uo",null,Nn,D),ln(qe,"".concat((Ke=t(r)!==-1&&t(r)<t(o)?"".concat(t(r)+1,"/"):"")!==null&&Ke!==void 0?Ke:"").concat((Ge=t(s))!==null&&Ge!==void 0?Ge:""))},[()=>({"jse-visible":t(x)!==""})],ae),ie("click",Ae,Ee),ie("click",Ue,Se),ie("click",Fn,I),ie("keydown",V,z),A(K,Me)};Q(P,K=>{d()&&K(pe)}),A(e,ee),nn()}var Oi=Symbol("path");function Kx(e,n){var o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:1/0,r={};Array.isArray(e)&&function(a,i,l){if(a.length<i)a.forEach(l);else for(var c=i>1?(a.length-1)/(i-1):a.length,d=0;d<i;d++){var u=Math.floor(d*c);l(a[u],u,a)}}(e,o,a=>{gt(a)?Eh(a,r,n):r[Oi]=!0});var s=[];return Oi in r&&s.push([]),Ah(r,[],s,n),s}function Eh(e,n,o){for(var r in e){var s=e[r],a=n[r]||(n[r]={});gt(s)&&o?Eh(s,a,o):a[Oi]===void 0&&(a[Oi]=!0)}}function Ah(e,n,o,r){for(var s in e){var a=n.concat(s),i=e[s];i&&i[Oi]===!0&&o.push(a),Oo(i)&&r&&Ah(i,a,o,r)}}function Qx(e,n,o,r,s,a){for(var i=arguments.length>6&&arguments[6]!==void 0?arguments[6]:80,l=wo(o)?o.length:0,c=function(k,$){var S=Object.values(k);if(Mt(S))return $;var y=(R,N)=>R+N;return S.reduce(y)/S.length}(r,s),d=e-i,u=n+2*i,h=k=>r[k]||s,p=0,m=a;m<d&&p<l;)m+=h(p),p++;p>0&&(m-=h(--p));for(var g=p,b=0;b<u&&g<l;)b+=h(g),g++;for(var j=0,x=g;x<l;x++)j+=h(x);return{startIndex:p,endIndex:g,startHeight:m,endHeight:j,averageItemHeight:c,visibleHeight:b,visibleItems:wo(o)?o.slice(p,g):[]}}function Xv(e,n,o,r){for(var{rowIndex:s}=nr(e,n),a=0,i=0;i<s;i++)a+=o[i]||r;return a}function nr(e,n){var[o,...r]=e,s=parseInt(o,10);return{rowIndex:isNaN(s)?-1:s,columnIndex:n.findIndex(a=>oa(r,a))}}function Aa(e,n){var{rowIndex:o,columnIndex:r}=e;return[String(o),...n[r]]}function Gx(e,n){var[o,r]=vm(e,i=>Zu(i.path[0])),s=fm(o,Xx),a=pm(s,i=>{var l={row:[],columns:{}};return i.forEach(c=>{var d=function(u,h){var p=nr(u.path,h);return p.columnIndex!==-1?p.columnIndex:-1}(c,n);d!==-1?(l.columns[d]===void 0&&(l.columns[d]=[]),l.columns[d].push(c)):l.row.push(c)}),l});return{root:r,rows:a}}function Wc(e,n){if(n&&n.length!==0)return n.length===1?n[0]:{path:e,message:"Multiple validation issues: "+n.map(o=>vr(o.path)+" "+o.message).join(", "),severity:pr.warning}}function Xx(e){return parseInt(e.path[0],10)}function Zx(e,n,o){var r=n.some(s=>function(a,i,l){if(!a)return!1;if(i.op==="replace"){var c=Sr(i.path),{rowIndex:d,columnIndex:u}=nr(c,l),h=l.findIndex(p=>et(p,a.path));if(d!==-1&&u!==-1&&u!==h)return!1}return!0}(e,s,o));return r?void 0:e}var No=So("jsoneditor:actions");function Rh(e){return Mu.apply(this,arguments)}function Mu(){return Mu=bn(function*(e){var{json:n,selection:o,indentation:r,readOnly:s,parser:a,onPatch:i}=e;if(!s&&n!==void 0&&o&&ds(o)){var l=th(n,o,r,a);if(l!==void 0){No("cut",{selection:o,clipboard:l,indentation:r}),yield yd(l);var{operations:c,newSelection:d}=lh(n,o);i(c,(u,h)=>({state:h,selection:d}))}}}),Mu.apply(this,arguments)}function Th(e){return $u.apply(this,arguments)}function $u(){return $u=bn(function*(e){var{json:n,selection:o,indentation:r,parser:s}=e,a=th(n,o,r,s);a!==void 0&&(No("copy",{clipboard:a,indentation:r}),yield yd(a))}),$u.apply(this,arguments)}function Ph(e){var{clipboardText:n,json:o,selection:r,readOnly:s,parser:a,onPatch:i,onChangeText:l,openRepairModal:c}=e;if(!s)try{d(n)}catch{c(n,h=>{No("repaired pasted text: ",h),d(h)})}function d(u){if(o!==void 0){var h=r||Yn([]),p=ih(o,h,u,a);No("paste",{pastedText:u,operations:p,ensureSelection:h}),i(p,(m,g)=>{var b=g;return p.filter(j=>(Hf(j)||Ju(j))&&po(j.value)).forEach(j=>{var x=Hr(o,j.path);b=Ba(m,b,x)}),{state:b}})}else No("paste text",{pastedText:u}),l(n,(m,g)=>{if(m)return{state:Ba(m,g,[])}})}}function Ih(e){var{json:n,text:o,selection:r,keepSelection:s,readOnly:a,onChange:i,onPatch:l}=e;if(!a&&r){var c=n!==void 0&&(Co(r)||wt(r))?qo(r.path,r.path):r;if(Mt(Be(r)))No("remove root",{selection:r}),i&&i({text:"",json:void 0},n!==void 0?{text:void 0,json:n}:{text:o||"",json:n},{contentErrors:void 0,patchResult:void 0});else if(n!==void 0){var{operations:d,newSelection:u}=lh(n,c);No("remove",{operations:d,selection:r,newSelection:u}),l(d,(h,p)=>({state:p,selection:s?r:u}))}}}function ql(e){var{insertType:n,selectInside:o,initialValue:r,json:s,selection:a,readOnly:i,parser:l,onPatch:c,onReplaceJson:d}=e;if(!i){var u=function(b,j,x){if(x==="object")return{};if(x==="array")return[];if(x==="structure"&&b!==void 0){var k=j?eh(j):[],$=Je(b,k);if(Array.isArray($)&&!Mt($)){var S=Yr($);return po(S)?Cm(S,y=>Array.isArray(y)?[]:gt(y)?void 0:""):""}}return""}(s,a,n);if(s!==void 0){var h=l.stringify(u),p=ih(s,a,h,l);No("onInsert",{insertType:n,operations:p,newValue:u,data:h});var m=Kn(p.filter(b=>b.op==="add"||b.op==="replace"));c(p,(b,j,x)=>{if(m){var k=Hr(b,m.path);if(po(u))return{state:cr(b,j,k,pd),selection:o?aa(k):x};if(u===""){var $=Mt(k)?void 0:Je(b,ot(k));return{state:cr(b,j,k,il),selection:gt($)?hd(k,r):Ol(k,r)}}}}),No("after patch")}else{No("onInsert",{insertType:n,newValue:u});var g=[];d(u,(b,j)=>({state:Ba(b,j,g),selection:po(u)?aa(g):Ol(g)}))}}}function qh(e){return Eu.apply(this,arguments)}function Eu(){return Eu=bn(function*(e){var{char:n,selectInside:o,json:r,selection:s,readOnly:a,parser:i,onPatch:l,onReplaceJson:c,onSelect:d}=e;a||(Co(s)?d(me(me({},s),{},{edit:!0,initialValue:n})):n==="{"?ql({insertType:"object",selectInside:o,initialValue:void 0,json:r,selection:s,readOnly:a,parser:i,onPatch:l,onReplaceJson:c}):n==="["?ql({insertType:"array",selectInside:o,initialValue:void 0,json:r,selection:s,readOnly:a,parser:i,onPatch:l,onReplaceJson:c}):wt(s)&&r!==void 0?po(Je(r,s.path))||d(me(me({},s),{},{edit:!0,initialValue:n})):(No("onInsertValueWithCharacter",{char:n}),yield function(u){return Au.apply(this,arguments)}({char:n,json:r,selection:s,readOnly:a,parser:i,onPatch:l,onReplaceJson:c})))}),Eu.apply(this,arguments)}function Au(){return Au=bn(function*(e){var{char:n,json:o,selection:r,readOnly:s,parser:a,onPatch:i,onReplaceJson:l}=e;s||ql({insertType:"value",selectInside:!1,initialValue:n,json:o,selection:r,readOnly:s,parser:a,onPatch:i,onReplaceJson:l})}),Au.apply(this,arguments)}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-json-preview.svelte-1vjn89h {
  flex: 1;
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  overflow: auto;
  white-space: pre-wrap;
  padding: 2px;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}`);var e1=L('<div class="jse-json-preview svelte-1vjn89h"> </div>');function Nh(e,n){en(n,!1);var o=E(),r=E(),s=f(n,"text",8),a=f(n,"json",8),i=f(n,"indentation",8),l=f(n,"parser",8);F(()=>(O(a()),O(s())),()=>{v(o,a()!==void 0?{json:a()}:{text:s()||""})}),F(()=>(t(o),O(i()),O(l()),pu),()=>{v(r,bi(du(t(o),i(),l()),pu))}),mt(),jn();var c=e1(),d=M(c);be(()=>ln(d,t(r))),A(e,c),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
button.jse-context-menu-button.svelte-1idfykj {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  flex: 1;
  white-space: nowrap;
  padding: var(--jse-padding, 10px);
  color: inherit;
}
button.jse-context-menu-button.svelte-1idfykj:hover {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
}
button.jse-context-menu-button.svelte-1idfykj:focus {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
  z-index: 1;
}
button.jse-context-menu-button.svelte-1idfykj:disabled {
  color: var(--jse-context-menu-color-disabled, #9d9d9d);
  background: unset;
}
button.jse-context-menu-button.left.svelte-1idfykj {
  text-align: left;
}
button.jse-context-menu-button.svelte-1idfykj svg {
  width: 16px;
}`);var n1=L('<button type="button"><!> <!></button>');function Vc(e,n){en(n,!1);var o=f(n,"item",8),r=f(n,"className",8,void 0),s=f(n,"onRequestClose",8);jn();var a=n1(),i=M(a),l=u=>{nt(u,{get data(){return o().icon}})};Q(i,u=>{o().icon&&u(l)});var c=q(i,2),d=u=>{var h=zo();be(()=>ln(h,o().text)),A(u,h)};Q(c,u=>{o().text&&u(d)}),be(u=>{kn(a,1,u,"svelte-1idfykj"),ht(a,"title",o().title),a.disabled=o().disabled||!1},[()=>za(Fs("jse-context-menu-button",r(),o().className))],ae),ie("click",a,u=>{s()(),o().onClick(u)}),A(e,a),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-dropdown-button.svelte-11rxb2m {
  flex: 1;
  line-height: normal;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  position: relative;
  padding: 0;
  display: flex;
}
.jse-dropdown-button.svelte-11rxb2m ul:where(.svelte-11rxb2m) {
  margin: 0;
  padding: 0;
}
.jse-dropdown-button.svelte-11rxb2m ul:where(.svelte-11rxb2m) li:where(.svelte-11rxb2m) {
  margin: 0;
  padding: 0;
  list-style-type: none;
}
.jse-dropdown-button.svelte-11rxb2m button.jse-open-dropdown:where(.svelte-11rxb2m) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  width: 2em;
  background: var(--jse-context-menu-background, #656565);
  color: var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff));
  border-radius: 0;
}
.jse-dropdown-button.svelte-11rxb2m button.jse-open-dropdown.jse-visible:where(.svelte-11rxb2m) {
  background: var(--jse-context-menu-background, #656565);
}
.jse-dropdown-button.svelte-11rxb2m button.jse-open-dropdown:where(.svelte-11rxb2m):hover {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
}
.jse-dropdown-button.svelte-11rxb2m button.jse-open-dropdown:where(.svelte-11rxb2m):focus {
  z-index: 1;
}
.jse-dropdown-button.svelte-11rxb2m button.jse-open-dropdown:where(.svelte-11rxb2m):disabled {
  color: var(--jse-context-menu-color-disabled, #9d9d9d);
  background: unset;
}
.jse-dropdown-button.svelte-11rxb2m .jse-dropdown-items:where(.svelte-11rxb2m) {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 1;
  background: var(--jse-context-menu-background, #656565);
  color: var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff));
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
}
.jse-dropdown-button.svelte-11rxb2m .jse-dropdown-items.jse-visible:where(.svelte-11rxb2m) {
  display: block;
}
.jse-dropdown-button.svelte-11rxb2m .jse-dropdown-items:where(.svelte-11rxb2m) button:where(.svelte-11rxb2m) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  width: 100%;
  text-align: left;
  padding: var(--jse-padding, 10px);
  margin: 0;
}
.jse-dropdown-button.svelte-11rxb2m .jse-dropdown-items:where(.svelte-11rxb2m) button:where(.svelte-11rxb2m):hover {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
}
.jse-dropdown-button.svelte-11rxb2m .jse-dropdown-items:where(.svelte-11rxb2m) button:where(.svelte-11rxb2m):disabled {
  color: var(--jse-context-menu-color-disabled, #9d9d9d);
  background: unset;
}`);var t1=L('<li class="svelte-11rxb2m"><button type="button"><!> </button></li>'),o1=L('<div role="button" tabindex="0" class="jse-dropdown-button svelte-11rxb2m"><!> <button type="button" data-type="jse-open-dropdown"><!></button> <div><ul class="svelte-11rxb2m"></ul></div></div>');fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
button.jse-context-menu-button.svelte-1idfykj {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  flex: 1;
  white-space: nowrap;
  padding: var(--jse-padding, 10px);
  color: inherit;
}
button.jse-context-menu-button.svelte-1idfykj:hover {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
}
button.jse-context-menu-button.svelte-1idfykj:focus {
  background: var(--jse-context-menu-background-highlight, #7a7a7a);
  z-index: 1;
}
button.jse-context-menu-button.svelte-1idfykj:disabled {
  color: var(--jse-context-menu-color-disabled, #9d9d9d);
  background: unset;
}
button.jse-context-menu-button.left.svelte-1idfykj {
  text-align: left;
}
button.jse-context-menu-button.svelte-1idfykj svg {
  width: 16px;
}`);var r1=L('<button type="button" slot="defaultItem"><!> </button>');function Hc(e,n){en(n,!1);var o=E(),r=f(n,"item",8),s=f(n,"className",8,void 0),a=f(n,"onRequestClose",8);F(()=>(O(r()),O(a())),()=>{v(o,r().items.map(i=>me(me({},i),{},{onClick:l=>{a()(),i.onClick(l)}})))}),mt(),jn(),function(i,l){en(l,!1);var c=E(void 0,!0),d=f(l,"items",25,()=>[]),u=f(l,"title",9,void 0),h=f(l,"width",9,"120px"),p=E(!1,!0);function m(){v(p,!1)}function g(y){ta(y)==="Escape"&&(y.preventDefault(),v(p,!1))}Po(()=>{document.addEventListener("click",m),document.addEventListener("keydown",g)}),rr(()=>{document.removeEventListener("click",m),document.removeEventListener("keydown",g)}),F(()=>O(d()),()=>{v(c,d().every(y=>y.disabled===!0))}),mt(),jn(!0);var b=o1(),j=M(b);io(j,l,"defaultItem",{},null);var x,k=q(j,2);nt(M(k),{data:Ca});var $,S=q(k,2);lo(M(S),5,d,ho,(y,R)=>{var N=t1(),_=M(N),z=M(_),W=H=>{nt(H,{get data(){return t(R).icon}})};Q(z,H=>{t(R).icon&&H(W)});var X=q(z);be(()=>{var H;ht(_,"title",t(R).title),_.disabled=t(R).disabled,kn(_,1,za(t(R).className),"svelte-11rxb2m"),ln(X," ".concat((H=t(R).text)!==null&&H!==void 0?H:""))}),ie("click",_,H=>t(R).onClick(H)),A(y,N)}),be((y,R)=>{var N;ht(b,"title",u()),x=kn(k,1,"jse-open-dropdown svelte-11rxb2m",null,x,y),k.disabled=t(c),$=kn(S,1,"jse-dropdown-items svelte-11rxb2m",null,$,R),fr(S,"width: ".concat((N=h())!==null&&N!==void 0?N:"",";"))},[()=>({"jse-visible":t(p)}),()=>({"jse-visible":t(p)})],ae),ie("click",k,function(){var y=t(p);setTimeout(()=>v(p,!y))}),ie("click",b,m),A(i,b),nn()}(e,{get width(){return r().width},get items(){return t(o)},$$slots:{defaultItem:(i,l)=>{var c=r1(),d=M(c),u=p=>{nt(p,{get data(){return r().main.icon}})};Q(d,p=>{r().main.icon&&p(u)});var h=q(d);be(p=>{var m;kn(c,1,p,"svelte-1idfykj"),ht(c,"title",r().main.title),c.disabled=r().main.disabled||!1,ln(h," ".concat((m=r().main.text)!==null&&m!==void 0?m:""))},[()=>za(Fs("jse-context-menu-button",s(),r().main.className))],ae),ie("click",c,p=>{a()(),r().main.onClick(p)}),A(i,c)}}}),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-contextmenu.svelte-12z7bz1 {
  box-shadow: var(--jse-controls-box-shadow, 0 2px 6px 0 rgba(0, 0, 0, 0.24));
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  background: var(--jse-context-menu-background, #656565);
  color: var(--jse-context-menu-color, var(--jse-text-color-inverse, #fff));
}
.jse-contextmenu.svelte-12z7bz1 .jse-row:where(.svelte-12z7bz1) {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: stretch;
}
.jse-contextmenu.svelte-12z7bz1 .jse-row:where(.svelte-12z7bz1) div.jse-label:where(.svelte-12z7bz1) {
  flex: 1;
  white-space: nowrap;
  padding: var(--jse-padding, 10px);
  color: var(--jse-context-menu-color-disabled, #9d9d9d);
  line-height: normal;
}
.jse-contextmenu.svelte-12z7bz1 .jse-row:where(.svelte-12z7bz1) div.jse-tip:where(.svelte-12z7bz1) {
  flex: 1;
  background: var(--jse-context-menu-tip-background, rgba(255, 255, 255, 0.2));
  color: var(--context-menu-tip-color, inherit);
  margin: calc(0.5 * var(--jse-padding, 10px));
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
  font-size: 80%;
  line-height: 1.3em;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: var(--jse-padding, 10px);
  border-radius: 3px;
}
.jse-contextmenu.svelte-12z7bz1 .jse-row:where(.svelte-12z7bz1) div.jse-tip:where(.svelte-12z7bz1) div.jse-tip-icon:where(.svelte-12z7bz1) {
  padding-top: calc(0.5 * var(--jse-padding, 10px));
}
.jse-contextmenu.svelte-12z7bz1 .jse-column:where(.svelte-12z7bz1) {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.jse-contextmenu.svelte-12z7bz1 .jse-column:where(.svelte-12z7bz1):not(:last-child) {
  border-right: 1px solid var(--jse-context-menu-separator-color, #7a7a7a);
}
.jse-contextmenu.svelte-12z7bz1 .jse-separator:where(.svelte-12z7bz1) {
  width: 100%;
  height: 1px;
  background: var(--jse-context-menu-separator-color, #7a7a7a);
}`);var a1=L('<div class="jse-separator svelte-12z7bz1"></div>'),s1=L('<div class="jse-label svelte-12z7bz1"> </div>'),i1=L('<div class="jse-column svelte-12z7bz1"></div>'),l1=L('<div class="jse-separator svelte-12z7bz1"></div>'),c1=L('<div class="jse-row svelte-12z7bz1"></div>'),u1=L('<div class="jse-separator svelte-12z7bz1"></div>'),d1=L('<div class="jse-row svelte-12z7bz1"><div class="jse-tip svelte-12z7bz1"><div class="jse-tip-icon svelte-12z7bz1"><!></div> <div class="jse-tip-text"> </div></div></div>'),v1=L('<div role="menu" tabindex="-1" class="jse-contextmenu svelte-12z7bz1"><!> <!></div>');function Uh(e,n){en(n,!1);var o=f(n,"items",9),r=f(n,"onRequestClose",9),s=f(n,"tip",9),a=E(void 0,!0);Po(()=>{var p=Array.from(t(a).querySelectorAll("button")).find(m=>!m.disabled);p&&p.focus()});var i={ArrowUp:"Up",ArrowDown:"Down",ArrowLeft:"Left",ArrowRight:"Right"};function l(p){return console.error("Unknown type of context menu item",p),"???"}jn(!0);var c=v1(),d=M(c);lo(d,1,o,ho,(p,m)=>{var g=to(),b=rn(g),j=k=>{Vc(k,{get item(){return t(m)},get onRequestClose(){return r()}})},x=(k,$)=>{var S=R=>{Hc(R,{get item(){return t(m)},get onRequestClose(){return r()}})},y=(R,N)=>{var _=W=>{var X=c1();lo(X,5,()=>t(m).items,ho,(H,ne)=>{var ye=to(),te=rn(ye),ve=Ee=>{Vc(Ee,{get item(){return t(ne)},get onRequestClose(){return r()}})},Re=(Ee,fe)=>{var Se=Ne=>{Hc(Ne,{get item(){return t(ne)},get onRequestClose(){return r()}})},we=(Ne,de)=>{var oe=an=>{var Mn=i1();lo(Mn,5,()=>t(ne).items,ho,(Y,I)=>{var ee=to(),P=rn(ee),pe=Me=>{Vc(Me,{className:"left",get item(){return t(I)},get onRequestClose(){return r()}})},K=(Me,V)=>{var T=Qe=>{Hc(Qe,{className:"left",get item(){return t(I)},get onRequestClose(){return r()}})},xn=(Qe,cn)=>{var sn=Xe=>{A(Xe,a1())},je=(Xe,$n)=>{var De=ge=>{var qe=s1(),Ae=M(qe);be(()=>ln(Ae,t(I).text)),A(ge,qe)},Nn=ge=>{var qe=zo();be(Ae=>ln(qe,Ae),[()=>l(t(I))],ae),A(ge,qe)};Q(Xe,ge=>{c0(t(I))?ge(De):ge(Nn,!1)},$n)};Q(Qe,Xe=>{sl(t(I))?Xe(sn):Xe(je,!1)},cn)};Q(Me,Qe=>{Pc(t(I))?Qe(T):Qe(xn,!1)},V)};Q(P,Me=>{ci(t(I))?Me(pe):Me(K,!1)}),A(Y,ee)}),A(an,Mn)},ce=(an,Mn)=>{var Y=ee=>{A(ee,l1())},I=ee=>{var P=zo();be(pe=>ln(P,pe),[()=>l(t(ne))],ae),A(ee,P)};Q(an,ee=>{sl(t(ne))?ee(Y):ee(I,!1)},Mn)};Q(Ne,an=>{d0(t(ne))?an(oe):an(ce,!1)},de)};Q(Ee,Ne=>{Pc(t(ne))?Ne(Se):Ne(we,!1)},fe)};Q(te,Ee=>{ci(t(ne))?Ee(ve):Ee(Re,!1)}),A(H,ye)}),A(W,X)},z=(W,X)=>{var H=ye=>{A(ye,u1())},ne=ye=>{var te=zo();be(ve=>ln(te,ve),[()=>l(t(m))],ae),A(ye,te)};Q(W,ye=>{sl(t(m))?ye(H):ye(ne,!1)},X)};Q(R,W=>{u0(t(m))?W(_):W(z,!1)},N)};Q(k,R=>{Pc(t(m))?R(S):R(y,!1)},$)};Q(b,k=>{ci(t(m))?k(j):k(x,!1)}),A(p,g)});var u=q(d,2),h=p=>{var m=d1(),g=M(m),b=M(g);nt(M(b),{data:zm});var j=M(q(b,2));be(()=>ln(j,s())),A(p,m)};Q(u,p=>{s()&&p(h)}),Yt(c,p=>v(a,p),()=>t(a)),ie("keydown",c,function(p){var m=ta(p),g=i[m];if(g&&p.target){p.preventDefault();var b=Lb({allElements:Array.from(t(a).querySelectorAll("button:not([disabled])")),currentElement:p.target,direction:g,hasPrio:j=>j.getAttribute("data-type")!=="jse-open-dropdown"});b&&b.focus()}}),A(e,c),nn()}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-value.jse-string.svelte-6ttr41 {
  color: var(--jse-value-color-string, #008000);
}
.jse-value.jse-object.svelte-6ttr41, .jse-value.jse-array.svelte-6ttr41 {
  min-width: 16px;
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
}
.jse-value.jse-number.svelte-6ttr41 {
  color: var(--jse-value-color-number, #ee422e);
}
.jse-value.jse-boolean.svelte-6ttr41 {
  color: var(--jse-value-color-boolean, #ff8c00);
}
.jse-value.jse-null.svelte-6ttr41 {
  color: var(--jse-value-color-null, #004ed0);
}
.jse-value.jse-invalid.svelte-6ttr41 {
  color: var(--jse-text-color, #4d4d4d);
}
.jse-value.jse-url.svelte-6ttr41 {
  color: var(--jse-value-color-url, #008000);
  text-decoration: underline;
}

.jse-enum-value.svelte-6ttr41 {
  background: var(--jse-hover-background-color, rgba(0, 0, 0, 0.06));
  border: none;
  padding: 0;
  font-family: inherit;
  font-size: inherit;
  cursor: pointer;
  outline: none;
}
.jse-enum-value.jse-selected.svelte-6ttr41 {
  background: var(--jse-selection-background-color, #d3d3d3);
  color: inherit;
}
.jse-enum-value.jse-value.svelte-6ttr41:focus {
  color: var(--jse-text-color, #4d4d4d);
}`);L("<option> </option>");L("<select></select>");var el,nl;function tl(e,n){return el||(nl=new WeakMap,el=new ResizeObserver(o=>{for(var r of o){var s=nl.get(r.target);s&&s(r.target)}})),nl.set(e,n),el.observe(e),{destroy:()=>{nl.delete(e),el.unobserve(e)}}}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-tree-mode.svelte-vrx1dr {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--jse-background-color, #fff);
  min-width: 0;
  min-height: 0;
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: var(--jse-text-color, #4d4d4d);
  line-height: var(--jse-line-height, calc(1em + 4px));
}
.jse-tree-mode.svelte-vrx1dr .jse-hidden-input-label:where(.svelte-vrx1dr) .jse-hidden-input:where(.svelte-vrx1dr) {
  position: fixed;
  top: -10px;
  left: -10px;
  width: 1px;
  height: 1px;
  padding: 0;
  border: 0;
  outline: none;
}
.jse-tree-mode.no-main-menu.svelte-vrx1dr {
  border-top: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-tree-mode.svelte-vrx1dr .jse-search-box-container:where(.svelte-vrx1dr) {
  position: relative;
  height: 0;
  top: var(--jse-padding, 10px);
  margin-right: calc(var(--jse-padding, 10px) + 20px);
  margin-left: var(--jse-padding, 10px);
  text-align: right;
  z-index: 3;
}
.jse-tree-mode.svelte-vrx1dr .jse-contents:where(.svelte-vrx1dr) {
  flex: 1;
  overflow: auto;
  position: relative;
  padding: 2px;
  display: flex;
  flex-direction: column;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-tree-mode.svelte-vrx1dr .jse-contents:where(.svelte-vrx1dr):last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-tree-mode.svelte-vrx1dr .jse-contents:where(.svelte-vrx1dr) .jse-loading-space:where(.svelte-vrx1dr) {
  flex: 1;
}
.jse-tree-mode.svelte-vrx1dr .jse-contents:where(.svelte-vrx1dr) .jse-loading:where(.svelte-vrx1dr) {
  flex: 2;
  text-align: center;
  color: var(--jse-panel-color-readonly, #b2b2b2);
  box-sizing: border-box;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
}
.jse-tree-mode.svelte-vrx1dr .jse-contents:where(.svelte-vrx1dr) .jse-search-box-background:where(.svelte-vrx1dr) {
  border: 50px solid var(--jse-modal-background, #f5f5f5);
  margin: -2px;
  margin-bottom: 2px;
  display: inline-block;
}`);var f1=L("<!> <!>",1),p1=L('<div class="jse-search-box-background svelte-vrx1dr"></div>'),h1=L('<div class="jse-search-box-container svelte-vrx1dr"><!></div> <div class="jse-contents svelte-vrx1dr"><!> <!></div> <!> <!> <!>',1),g1=L('<label class="jse-hidden-input-label svelte-vrx1dr"><input type="text" tabindex="-1" class="jse-hidden-input svelte-vrx1dr"></label> <!>',1),m1=L('<div class="jse-contents svelte-vrx1dr"><div class="jse-loading-space svelte-vrx1dr"></div> <div class="jse-loading svelte-vrx1dr">loading...</div></div>'),b1=L('<div role="tree" tabindex="-1"><!> <!> <!></div> <!> <!>',1);function Ru(e,n){en(n,!1);var o=E(void 0,!0),r=So("jsoneditor:TreeMode"),s=typeof window>"u";r("isSSR:",s);var a=ys(),i=ys(),{openAbsolutePopup:l,closeAbsolutePopup:c}=Ma("absolute-popup"),d=E(void 0,!0),u=E(void 0,!0),h=E(void 0,!0),p=!1,m=kh(),g=f(n,"readOnly",9),b=f(n,"externalContent",9),j=f(n,"externalSelection",9),x=f(n,"history",9),k=f(n,"truncateTextSize",9),$=f(n,"mainMenuBar",9),S=f(n,"navigationBar",9),y=f(n,"escapeControlCharacters",9),R=f(n,"escapeUnicodeCharacters",9),N=f(n,"parser",9),_=f(n,"parseMemoizeOne",9),z=f(n,"validator",9),W=f(n,"validationParser",9),X=f(n,"pathParser",9),H=f(n,"indentation",9),ne=f(n,"onError",9),ye=f(n,"onChange",9),te=f(n,"onChangeMode",9),ve=f(n,"onSelect",9),Re=f(n,"onUndo",9),Ee=f(n,"onRedo",9),fe=f(n,"onRenderValue",9),Se=f(n,"onRenderMenu",9),we=f(n,"onRenderContextMenu",9),Ne=f(n,"onClassName",9),de=f(n,"onFocus",9),oe=f(n,"onBlur",9),ce=f(n,"onSortModal",9),an=f(n,"onTransformModal",9),Mn=f(n,"onJSONEditorModal",9),Y=!1,I=E(!1,!0),ee=E(void 0,!0);jd({onMount:Po,onDestroy:rr,getWindow:()=>Ni(t(h)),hasFocus:()=>Y&&document.hasFocus()||od(t(h)),onFocus:()=>{p=!0,de()&&de()()},onBlur:()=>{p=!1,oe()&&oe()()}});var P=E(void 0,!0),pe=E(void 0,!0),K=void 0,Me=!1,V=E(gu({json:t(P)}),!0),T=E(wi(j())?j():void 0,!0);function xn(w){v(T,w)}Po(()=>{if(t(T)){var w=Be(t(T));v(V,cr(t(P),t(V),w,il)),setTimeout(()=>At(w))}});var Qe,cn=E(void 0,!0),sn=E(void 0,!0),je=E(void 0,!0),Xe=E(!1,!0),$n=E(!1,!0);function De(w){v(je,(Qe=w)?uh(t(P),Qe.items):void 0)}function Nn(w,J){return ge.apply(this,arguments)}function ge(){return(ge=bn(function*(w,J){v(V,cr(t(P),t(V),w,il));var se=St(J);yield En(w,{element:se})})).apply(this,arguments)}function qe(){v(Xe,!1),v($n,!1),$e()}function Ae(w){r("select validation error",w),v(T,Yn(w.path)),En(w.path)}function Ue(w){var J=arguments.length>1&&arguments[1]!==void 0?arguments[1]:Ev;r("expand"),v(V,cr(t(P),t(V),w,J))}function Fn(w,J){v(V,zv(t(P),t(V),w,J)),t(T)&&function(se,Ie){return oa(Be(se),Ie)&&(Be(se).length>Ie.length||Ro(se))}(t(T),w)&&v(T,void 0)}var gn=E(!1,!0),Ve=E([],!0),D=E(void 0,!0),Ke=vi(Ch);function Ge(w,J,se,Ie){fs(()=>{var Oe;try{Oe=Ke(w,J,se,Ie)}catch(Ce){Oe=[{path:[],message:"Failed to validate: "+Ce.message,severity:pr.warning}]}et(Oe,t(Ve))||(r("validationErrors changed:",Oe),v(Ve,Oe),v(D,function(Ce,ze){var Fe;return ze.forEach(wn=>{Fe=Kv(Ce,Fe,wn.path,(tt,Hn)=>me(me({},Hn),{},{validationError:wn}))}),ze.forEach(wn=>{for(var tt=wn.path;tt.length>0;)tt=ot(tt),Fe=Kv(Ce,Fe,tt,(Hn,Ot)=>Ot.validationError?Ot:me(me({},Ot),{},{validationError:{isChildError:!0,path:tt,message:"Contains invalid data",severity:pr.warning}}))}),Fe}(w,t(Ve))))},Oe=>r("validationErrors updated in ".concat(Oe," ms")))}function Ct(){return r("validate"),K?{parseError:K,isRepairable:!1}:(Ge(t(P),z(),N(),W()),Mt(t(Ve))?void 0:{validationErrors:t(Ve)})}function _e(){return t(P)}function Rn(){return t(V)}function Un(){return t(T)}function co(w){r("applyExternalContent",{updatedContent:w}),mi(w)?function(J){if(J!==void 0){var se=!et(t(P),J);if(r("update external json",{isChanged:se,currentlyText:t(P)===void 0}),!!se){var Ie={documentState:t(V),selection:t(T),json:t(P),text:t(pe),textIsRepaired:t(gn)};v(P,J),v(V,Yo(J,t(V))),ut(t(P)),v(pe,void 0),v(gn,!1),K=void 0,_n(t(P)),Lt(Ie)}}}(w.json):gi(w)&&function(J){if(!(J===void 0||mi(b()))){var se=J!==t(pe);if(r("update external text",{isChanged:se}),!!se){var Ie={documentState:t(V),selection:t(T),json:t(P),text:t(pe),textIsRepaired:t(gn)};try{v(P,_()(J)),v(V,Yo(t(P),t(V))),ut(t(P)),v(pe,J),v(gn,!1),K=void 0}catch(Oe){try{v(P,_()(Or(J))),v(V,Yo(t(P),t(V))),ut(t(P)),v(pe,J),v(gn,!0),K=void 0,_n(t(P))}catch{v(P,void 0),v(V,void 0),v(pe,b().text),v(gn,!1),K=t(pe)!==void 0&&t(pe)!==""?Ss(t(pe),Oe.message||String(Oe)):void 0}}_n(t(P)),Lt(Ie)}}}(w.text)}function ut(w){Me||(Me=!0,v(V,Ba(w,t(V),[])))}function _n(w){t(T)&&(pa(w,qa(t(T)))&&pa(w,Be(t(T)))||(r("clearing selection: path does not exist anymore",t(T)),v(T,rs(w,t(V)))))}function Lt(w){if(w.json!==void 0||w.text!==void 0){var J=t(P)!==void 0&&w.json!==void 0;x().add({type:"tree",undo:{patch:J?[{op:"replace",path:"",value:w.json}]:void 0,json:w.json,text:w.text,documentState:w.documentState,textIsRepaired:w.textIsRepaired,selection:Cr(w.selection),sortedColumn:void 0},redo:{patch:J?[{op:"replace",path:"",value:t(P)}]:void 0,json:t(P),text:t(pe),documentState:t(V),textIsRepaired:t(gn),selection:Cr(t(T)),sortedColumn:void 0}})}}function Qn(w,J){var se;if(r("patch",w,J),t(P)===void 0)throw new Error("Cannot apply patch: no JSON");var Ie=t(P),Oe={json:void 0,text:t(pe),documentState:t(V),selection:Cr(t(T)),textIsRepaired:t(gn),sortedColumn:void 0},Ce=ch(t(P),w),ze=Gp(t(P),t(V),w),Fe=(se=Os(t(P),w))!==null&&se!==void 0?se:t(T),wn=typeof J=="function"?J(ze.json,ze.documentState,Fe):void 0;return v(P,(wn==null?void 0:wn.json)!==void 0?wn.json:ze.json),v(V,(wn==null?void 0:wn.state)!==void 0?wn.state:ze.documentState),v(T,(wn==null?void 0:wn.selection)!==void 0?wn.selection:Fe),v(pe,void 0),v(gn,!1),v(sn,void 0),K=void 0,_n(t(P)),x().add({type:"tree",undo:me({patch:Ce},Oe),redo:{patch:w,json:void 0,text:t(pe),documentState:t(V),selection:Cr(t(T)),sortedColumn:void 0,textIsRepaired:t(gn)}}),{json:t(P),previousJson:Ie,undo:Ce,redo:w}}function ft(){!g()&&t(T)&&v(T,hd(Be(t(T))))}function rt(){if(!g()&&t(T)){var w=Be(t(T)),J=Je(t(P),w);po(J)?function(se,Ie){r("openJSONEditorModal",{path:se,value:Ie}),Y=!0,Mn()({content:{json:Ie},path:se,onPatch:t(ro).onPatch,onClose:()=>{Y=!1,setTimeout($e)}})}(w,J):v(T,Ol(w))}}function Ln(){if(!g()&&wt(t(T))){var w=Be(t(T)),J=Sn(w),se=Je(t(P),w),Ie=!Xr(t(P),t(V),w),Oe=Ie?String(se):_s(String(se),N());r("handleToggleEnforceString",{enforceString:Ie,value:se,updatedValue:Oe}),Pn([{op:"replace",path:J,value:Oe}],(Ce,ze)=>({state:nc(t(P),ze,w,{type:"value",enforceString:Ie})}))}}function yn(){return t(gn)&&t(P)!==void 0&&qn(t(P)),t(P)!==void 0?{json:t(P)}:{text:t(pe)||""}}function st(){return on.apply(this,arguments)}function on(){return on=bn(function*(){var w=!(arguments.length>0&&arguments[0]!==void 0)||arguments[0];yield Rh({json:t(P),selection:t(T),indentation:w?H():void 0,readOnly:g(),parser:N(),onPatch:Pn})}),on.apply(this,arguments)}function Jn(){return Wn.apply(this,arguments)}function Wn(){return Wn=bn(function*(){var w=!(arguments.length>0&&arguments[0]!==void 0)||arguments[0];t(P)!==void 0&&(yield Th({json:t(P),selection:t(T),indentation:w?H():void 0,parser:N()}))}),Wn.apply(this,arguments)}function Et(w){var J;w.preventDefault(),vo((J=w.clipboardData)===null||J===void 0?void 0:J.getData("text/plain"))}function Kt(){return bt.apply(this,arguments)}function bt(){return(bt=bn(function*(){try{vo(yield navigator.clipboard.readText())}catch(w){console.error(w),v(I,!0)}})).apply(this,arguments)}function vo(w){w!==void 0&&Ph({clipboardText:w,json:t(P),selection:t(T),readOnly:g(),parser:N(),onPatch:Pn,onChangeText:Jt,openRepairModal:oo})}function oo(w,J){v(ee,{text:w,onParse:se=>Zl(se,Ie=>qi(Ie,N())),onRepair:Up,onApply:J,onClose:$e})}function uo(){Ih({json:t(P),text:t(pe),selection:t(T),keepSelection:!1,readOnly:g(),onChange:ye(),onPatch:Pn})}function jo(){!g()&&t(P)!==void 0&&t(T)&&ds&&!Mt(Be(t(T)))&&(r("duplicate",{selection:t(T)}),Pn(ah(t(P),Oa(t(P),t(T)))))}function fo(){g()||!t(T)||!Ht(t(T))&&!wt(t(T))||Mt(Be(t(T)))||(r("extract",{selection:t(T)}),Pn(sh(t(P),t(T)),(w,J)=>{if(po(w))return{state:qc(w,J,[])}}))}function Pt(w){ql({insertType:w,selectInside:!0,initialValue:void 0,json:t(P),selection:t(T),readOnly:g(),parser:N(),onPatch:Pn,onReplaceJson:qn})}function It(w){Co(t(T))&&v(T,Yn(t(T).path)),t(T)||v(T,rs(t(P),t(V))),Pt(w)}function Zt(w){if(!g()&&t(T))if(Qi(t(T)))try{var J=qa(t(T)),se=Je(t(P),J),Ie=function(Ce,ze,Fe){if(ze==="array"){if(Array.isArray(Ce))return Ce;if(gt(Ce))return pv(Ce);if(typeof Ce=="string")try{var wn=Fe.parse(Ce);if(Array.isArray(wn))return wn;if(gt(wn))return pv(wn)}catch{return[Ce]}return[Ce]}if(ze==="object"){if(Array.isArray(Ce))return fv(Ce);if(gt(Ce))return Ce;if(typeof Ce=="string")try{var tt=Fe.parse(Ce);if(gt(tt))return tt;if(Array.isArray(tt))return fv(tt)}catch{return{value:Ce}}return{value:Ce}}if(ze==="value")return po(Ce)?Fe.stringify(Ce):Ce;throw new Error("Cannot convert ".concat(ed(Ce,Fe)," to ").concat(ze))}(se,w,N());if(Ie===se)return;var Oe=[{op:"replace",path:Sn(J),value:Ie}];r("handleConvert",{selection:t(T),path:J,type:w,operations:Oe}),Pn(Oe,(Ce,ze)=>({state:t(T)?Ba(Ce,ze,Be(t(T))):t(V)}))}catch(Ce){ne()(Ce)}else ne()(new Error("Cannot convert current selection to ".concat(w)))}function le(){if(t(T)){var w=Av(t(P),t(V),t(T),!1),J=ot(Be(t(T)));w&&!Mt(Be(w))&&et(J,ot(Be(w)))?v(T,Zr(Be(w))):v(T,aa(J)),r("insert before",{selection:t(T),selectionBefore:w,parentPath:J}),Gt(),Ut()}}function Gn(){if(t(T)){var w=ka(t(P),t(T));r("insert after",w),v(T,Zr(w)),Gt(),Ut()}}function B(w){return ke.apply(this,arguments)}function ke(){return(ke=bn(function*(w){yield qh({char:w,selectInside:!0,json:t(P),selection:t(T),readOnly:g(),parser:N(),onPatch:Pn,onReplaceJson:qn,onSelect:xn})})).apply(this,arguments)}function He(){if(!g()&&x().canUndo){var w=x().undo();if(Sl(w)){var J={json:t(P),text:t(pe)};v(P,w.undo.patch?zr(t(P),w.undo.patch):w.undo.json),v(V,w.undo.documentState),v(T,w.undo.selection),v(pe,w.undo.text),v(gn,w.undo.textIsRepaired),K=void 0,r("undo",{item:w,json:t(P),documentState:t(V),selection:t(T)}),Dn(J,w.undo.patch&&w.redo.patch?{json:t(P),previousJson:J.json,redo:w.undo.patch,undo:w.redo.patch}:void 0),$e(),t(T)&&En(Be(t(T)),{scrollToWhenVisible:!1})}else Re()(w)}}function zn(){if(!g()&&x().canRedo){var w=x().redo();if(Sl(w)){var J={json:t(P),text:t(pe)};v(P,w.redo.patch?zr(t(P),w.redo.patch):w.redo.json),v(V,w.redo.documentState),v(T,w.redo.selection),v(pe,w.redo.text),v(gn,w.redo.textIsRepaired),K=void 0,r("redo",{item:w,json:t(P),documentState:t(V),selection:t(T)}),Dn(J,w.undo.patch&&w.redo.patch?{json:t(P),previousJson:J.json,redo:w.redo.patch,undo:w.undo.patch}:void 0),$e(),t(T)&&En(Be(t(T)),{scrollToWhenVisible:!1})}else Ee()(w)}}function We(w){var J;g()||t(P)===void 0||(Y=!0,ce()({id:a,json:t(P),rootPath:w,onSort:(J=bn(function*(se){var{operations:Ie}=se;r("onSort",w,Ie),Pn(Ie,(Oe,Ce)=>({state:qc(Oe,Ce,w),selection:Yn(w)}))}),function(se){return J.apply(this,arguments)}),onClose:()=>{Y=!1,setTimeout($e)}}))}function Tn(){t(T)&&We(Tv(t(P),t(T)))}function dt(){We([])}function On(w){if(t(P)!==void 0){var{id:J,onTransform:se,onClose:Ie}=w,Oe=w.rootPath||[];Y=!0,an()({id:J||i,json:t(P),rootPath:Oe,onTransform:Ce=>{se?se({operations:Ce,json:t(P),transformedJson:zr(t(P),Ce)}):(r("onTransform",Oe,Ce),Pn(Ce,(ze,Fe)=>({state:qc(ze,Fe,Oe),selection:Yn(Oe)})))},onClose:()=>{Y=!1,setTimeout($e),Ie&&Ie()}})}}function pt(){t(T)&&On({rootPath:Tv(t(P),t(T))})}function un(){On({rootPath:[]})}function En(w){return tn.apply(this,arguments)}function tn(){return tn=bn(function*(w){var{scrollToWhenVisible:J=!0,element:se}=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{};v(V,cr(t(P),t(V),w,il));var Ie=se??pn(w);if(r("scrollTo",{path:w,elem:Ie,refContents:t(d)}),!Ie||!t(d))return Promise.resolve();var Oe=t(d).getBoundingClientRect(),Ce=Ie.getBoundingClientRect();if(!J&&Ce.bottom>Oe.top&&Ce.top<Oe.bottom)return Promise.resolve();var ze=-Oe.height/4;return new Promise(Fe=>{m(Ie,{container:t(d),offset:ze,duration:300,callback:()=>Fe()})})}),tn.apply(this,arguments)}function pn(w){var J,se;return Gt(),(J=(se=t(d))===null||se===void 0?void 0:se.querySelector('div[data-path="'.concat(vu(w),'"]')))!==null&&J!==void 0?J:void 0}function St(w){var J,se;return Gt(),(J=(se=t(d))===null||se===void 0?void 0:se.querySelector('span[data-search-result-index="'.concat(w,'"]')))!==null&&J!==void 0?J:void 0}function At(w){var J=pn(w);if(J&&t(d)){var se=t(d).getBoundingClientRect(),Ie=J.getBoundingClientRect(),Oe=po(Je(t(P),w))?20:Ie.height;Ie.top<se.top+20?m(J,{container:t(d),offset:-20,duration:0}):Ie.top+Oe>se.bottom-20&&m(J,{container:t(d),offset:-(se.height-Oe-20),duration:0})}}function Dn(w,J){if(w.json!==void 0||(w==null?void 0:w.text)!==void 0){if(t(pe)!==void 0){var se,Ie={text:t(pe),json:void 0};(se=ye())===null||se===void 0||se(Ie,w,{contentErrors:Ct(),patchResult:J})}else if(t(P)!==void 0){var Oe,Ce={text:void 0,json:t(P)};(Oe=ye())===null||Oe===void 0||Oe(Ce,w,{contentErrors:Ct(),patchResult:J})}}}function Pn(w,J){r("handlePatch",w,J);var se={json:t(P),text:t(pe)},Ie=Qn(w,J);return Dn(se,Ie),Ie}function qn(w,J){var se={json:t(P),text:t(pe)},Ie={documentState:t(V),selection:t(T),json:t(P),text:t(pe),textIsRepaired:t(gn)},Oe=cr(t(P),Yo(w,t(V)),[],ii),Ce=typeof J=="function"?J(w,Oe,t(T)):void 0;v(P,(Ce==null?void 0:Ce.json)!==void 0?Ce.json:w),v(V,(Ce==null?void 0:Ce.state)!==void 0?Ce.state:Oe),v(T,(Ce==null?void 0:Ce.selection)!==void 0?Ce.selection:t(T)),v(pe,void 0),v(gn,!1),K=void 0,_n(t(P)),Lt(Ie),Dn(se,void 0)}function Jt(w,J){r("handleChangeText");var se={json:t(P),text:t(pe)},Ie={documentState:t(V),selection:t(T),json:t(P),text:t(pe),textIsRepaired:t(gn)};try{v(P,_()(w)),v(V,cr(t(P),Yo(t(P),t(V)),[],ii)),v(pe,void 0),v(gn,!1),K=void 0}catch(Ce){try{v(P,_()(Or(w))),v(V,cr(t(P),Yo(t(P),t(V)),[],ii)),v(pe,w),v(gn,!0),K=void 0}catch{v(P,void 0),v(V,gu({json:t(P),expand:ii})),v(pe,w),v(gn,!1),K=t(pe)!==""?Ss(t(pe),Ce.message||String(Ce)):void 0}}if(typeof J=="function"){var Oe=J(t(P),t(V),t(T));v(P,(Oe==null?void 0:Oe.json)!==void 0?Oe.json:t(P)),v(V,(Oe==null?void 0:Oe.state)!==void 0?Oe.state:t(V)),v(T,(Oe==null?void 0:Oe.selection)!==void 0?Oe.selection:t(T))}_n(t(P)),Lt(Ie),Dn(se,void 0)}function U(w,J){var se=arguments.length>2&&arguments[2]!==void 0&&arguments[2];r("handleExpand",{path:w,expanded:J,recursive:se}),J?Ue(w,se?pd:Ev):Fn(w,se),$e()}function Z(){U([],!0,!0)}function he(){U([],!1,!0)}function An(w){r("openFind",{findAndReplace:w}),v(Xe,!1),v($n,!1),Gt(),v(Xe,!0),v($n,w)}function it(w,J){r("handleExpandSection",w,J),v(V,function(se,Ie,Oe,Ce){return zs(se,Ie,Oe,(ze,Fe)=>{if(!To(Fe))return Fe;var wn=Yp(Fe.visibleSections.concat(Ce));return me(me({},Fe),{},{visibleSections:wn})})}(t(P),t(V),w,J))}function Xn(w){r("pasted json as text",w),v(sn,w)}function jt(w){var J,{anchor:se,left:Ie,top:Oe,width:Ce,height:ze,offsetTop:Fe,offsetLeft:wn,showTip:tt}=w,Hn=function(Tt){var{json:Dt,documentState:ct,selection:Le,readOnly:Ye,onEditKey:vt,onEditValue:Qt,onToggleEnforceString:ao,onCut:yt,onCopy:xt,onPaste:In,onRemove:go,onDuplicate:Ao,onExtract:sr,onInsertBefore:wr,onInsert:ir,onConvert:Mo,onInsertAfter:Do,onSort:_r,onTransform:Xa}=Tt,Zo=Dt!==void 0,mo=!!Le,Wt=!!Le&&Mt(Be(Le)),ko=Le?Je(Dt,Be(Le)):void 0,Dr=Array.isArray(ko)?"Edit array":gt(ko)?"Edit object":"Edit value",Jo=Zo&&(Ht(Le)||Co(Le)||wt(Le)),Ls=Le&&!Wt?Je(Dt,ot(Be(Le))):void 0,Js=!Ye&&Zo&&zl(Le)&&!Wt&&!Array.isArray(Ls),Za=!Ye&&Zo&&Le!==void 0&&zl(Le),vc=Za&&!po(ko),Bs=!Ye&&Jo,Ws=Jo,fc=!Ye&&mo,pc=!Ye&&Zo&&Jo&&!Wt,hc=!Ye&&Zo&&Le!==void 0&&(Ht(Le)||wt(Le))&&!Wt,Ft=Jo,Io=Ft?"Convert to:":"Insert:",Vs=!Ye&&(Ro(Le)&&Array.isArray(ko)||or(Le)&&Array.isArray(Ls)),Hs=!Ye&&(Ft?Qi(Le)&&!gt(ko):mo),Ea=!Ye&&(Ft?Qi(Le)&&!Array.isArray(ko):mo),Bo=!Ye&&(Ft?Qi(Le)&&po(ko):mo),gc=Le!==void 0&&Xr(Dt,ct,Be(Le));function es(Ys){Jo?Ys!=="structure"&&Mo(Ys):ir(Ys)}return[{type:"row",items:[{type:"button",onClick:()=>vt(),icon:ps,text:"Edit key",title:"Edit the key (Double-click on the key)",disabled:!Js},{type:"dropdown-button",main:{type:"button",onClick:()=>Qt(),icon:ps,text:Dr,title:"Edit the value (Double-click on the value)",disabled:!Za},width:"11em",items:[{type:"button",icon:ps,text:Dr,title:"Edit the value (Double-click on the value)",onClick:()=>Qt(),disabled:!Za},{type:"button",icon:gc?Lf:Jf,text:"Enforce string",title:"Enforce keeping the value as string when it contains a numeric value",onClick:()=>ao(),disabled:!vc}]}]},{type:"separator"},{type:"row",items:[{type:"dropdown-button",main:{type:"button",onClick:()=>yt(!0),icon:hs,text:"Cut",title:"Cut selected contents, formatted with indentation (Ctrl+X)",disabled:!Bs},width:"10em",items:[{type:"button",icon:hs,text:"Cut formatted",title:"Cut selected contents, formatted with indentation (Ctrl+X)",onClick:()=>yt(!0),disabled:!Bs},{type:"button",icon:hs,text:"Cut compacted",title:"Cut selected contents, without indentation (Ctrl+Shift+X)",onClick:()=>yt(!1),disabled:!Bs}]},{type:"dropdown-button",main:{type:"button",onClick:()=>xt(!0),icon:ha,text:"Copy",title:"Copy selected contents, formatted with indentation (Ctrl+C)",disabled:!Ws},width:"12em",items:[{type:"button",icon:ha,text:"Copy formatted",title:"Copy selected contents, formatted with indentation (Ctrl+C)",onClick:()=>xt(!0),disabled:!Ws},{type:"button",icon:ha,text:"Copy compacted",title:"Copy selected contents, without indentation (Ctrl+Shift+C)",onClick:()=>xt(!1),disabled:!Ws}]},{type:"button",onClick:()=>In(),icon:Bf,text:"Paste",title:"Paste clipboard contents (Ctrl+V)",disabled:!fc}]},{type:"separator"},{type:"row",items:[{type:"column",items:[{type:"button",onClick:()=>Ao(),icon:Wf,text:"Duplicate",title:"Duplicate selected contents (Ctrl+D)",disabled:!pc},{type:"button",onClick:()=>sr(),icon:hm,text:"Extract",title:"Extract selected contents",disabled:!hc},{type:"button",onClick:()=>_r(),icon:pl,text:"Sort",title:"Sort array or object contents",disabled:Ye||!Jo},{type:"button",onClick:()=>Xa(),icon:hl,text:"Transform",title:"Transform array or object contents (filter, sort, project)",disabled:Ye||!Jo},{type:"button",onClick:()=>go(),icon:Zc,text:"Remove",title:"Remove selected contents (Delete)",disabled:Ye||!Jo}]},{type:"column",items:[{type:"label",text:Io},{type:"button",onClick:()=>es("structure"),icon:Ft?Ji:cs,text:"Structure",title:Io+" structure like the first item in the array",disabled:!Vs},{type:"button",onClick:()=>es("object"),icon:Ft?Ji:cs,text:"Object",title:Io+" object",disabled:!Hs},{type:"button",onClick:()=>es("array"),icon:Ft?Ji:cs,text:"Array",title:Io+" array",disabled:!Ea},{type:"button",onClick:()=>es("value"),icon:Ft?Ji:cs,text:"Value",title:Io+" value",disabled:!Bo}]}]},{type:"separator"},{type:"row",items:[{type:"button",onClick:()=>wr(),icon:gm,text:"Insert before",title:"Select area before current entry to insert or paste contents",disabled:Ye||!Jo||Wt},{type:"button",onClick:()=>Do(),icon:mm,text:"Insert after",title:"Select area after current entry to insert or paste contents",disabled:Ye||!Jo||Wt}]}]}({json:t(P),documentState:t(V),selection:t(T),readOnly:g(),onEditKey:ft,onEditValue:rt,onToggleEnforceString:Ln,onCut:st,onCopy:Jn,onPaste:Kt,onRemove:uo,onDuplicate:jo,onExtract:fo,onInsertBefore:le,onInsert:It,onInsertAfter:Gn,onConvert:Zt,onSort:Tn,onTransform:pt}),Ot=(J=we()(Hn))!==null&&J!==void 0?J:Hn;if(Ot!==!1){var dn={left:Ie,top:Oe,offsetTop:Fe,offsetLeft:wn,width:Ce,height:ze,anchor:se,closeOnOuterClick:!0,onClose:()=>{Y=!1,$e()}};Y=!0;var eo=l(Uh,{tip:tt?"Tip: you can open this context menu via right-click or with Ctrl+Q":void 0,items:Ot,onRequestClose:()=>c(eo)},dn)}}function Ut(w){if(!Vo(t(T)))if(w&&(w.stopPropagation(),w.preventDefault()),w&&w.type==="contextmenu"&&w.target!==t(u))jt({left:w.clientX,top:w.clientY,width:Wr,height:Br,showTip:!1});else{var J,se=(J=t(d))===null||J===void 0?void 0:J.querySelector(".jse-context-menu-pointer.jse-selected");if(se)jt({anchor:se,offsetTop:2,width:Wr,height:Br,showTip:!1});else{var Ie,Oe=(Ie=t(d))===null||Ie===void 0?void 0:Ie.getBoundingClientRect();Oe&&jt({top:Oe.top+2,left:Oe.left+2,width:Wr,height:Br,showTip:!1})}}}function qt(w){jt({anchor:Vp(w.target,"BUTTON"),offsetTop:0,width:Wr,height:Br,showTip:!0})}function Bt(){return mn.apply(this,arguments)}function mn(){return(mn=bn(function*(){if(r("apply pasted json",t(sn)),t(sn)){var{onPasteAsJson:w}=t(sn);v(sn,void 0),w(),setTimeout($e)}})).apply(this,arguments)}function _t(){r("clear pasted json"),v(sn,void 0),$e()}function vn(){te()(yo.text)}function xo(w){v(T,w),$e(),En(Be(w))}function $e(){r("focus"),t(u)&&(t(u).focus(),t(u).select())}function at(w){return function(J,se,Ie){var Oe=ot(Ie),Ce=[Kn(Ie)],ze=Je(J,Oe),Fe=ze?Ic(ze,se,Ce):void 0;return Fe?Yn(Oe.concat(Fe)):Zr(Ie)}(t(P),t(V),w)}function zt(w){t(o)&&t(o).onDrag(w)}function lt(){t(o)&&t(o).onDragEnd()}var ro=E(void 0,!0);F(()=>t(T),()=>{var w;w=t(T),et(w,j())||(r("onSelect",w),ve()(w))}),F(()=>(O(y()),O(R())),()=>{v(cn,nd({escapeControlCharacters:y(),escapeUnicodeCharacters:R()}))}),F(()=>t(Xe),()=>{(function(w){t(d)&&w&&t(d).scrollTop===0&&(Ko(d,t(d).style.overflowAnchor="none"),Ko(d,t(d).scrollTop+=si),setTimeout(()=>{t(d)&&Ko(d,t(d).style.overflowAnchor="")}))})(t(Xe))}),F(()=>O(b()),()=>{co(b())}),F(()=>O(j()),()=>{(function(w){et(t(T),w)||(r("applyExternalSelection",{selection:t(T),externalSelection:w}),wi(w)&&v(T,w))})(j())}),F(()=>(t(P),O(z()),O(N()),O(W())),()=>{Ge(t(P),z(),N(),W())}),F(()=>(t(d),Yv),()=>{v(o,t(d)?Yv(t(d)):void 0)}),F(()=>(O(g()),O(k()),O(N()),t(cn),O(fe()),O(Ne())),()=>{v(ro,{mode:yo.tree,readOnly:g(),truncateTextSize:k(),parser:N(),normalization:t(cn),getJson:_e,getDocumentState:Rn,getSelection:Un,findElement:pn,findNextInside:at,focus:$e,onPatch:Pn,onInsert:Pt,onExpand:U,onSelect:xn,onFind:An,onExpandSection:it,onPasteJson:Xn,onRenderValue:fe(),onContextMenu:jt,onClassName:Ne()||(()=>{}),onDrag:zt,onDragEnd:lt})}),F(()=>t(ro),()=>{r("context changed",t(ro))}),mt(),jn(!0);var _o=b1();ie("mousedown",Qr,function(w){!Ds(w.target,J=>J===t(h))&&Vo(t(T))&&(r("click outside the editor, exit edit mode"),v(T,Cr(t(T))),p&&t(u)&&(t(u).focus(),t(u).blur()),r("blur (outside editor)"),t(u)&&t(u).blur())});var C,G=rn(_o),re=M(G),ue=w=>{(function(J,se){en(se,!1);var Ie=E(void 0,!0),Oe=E(void 0,!0),Ce=E(void 0,!0),ze=f(se,"json",9),Fe=f(se,"selection",9),wn=f(se,"readOnly",9),tt=f(se,"showSearch",13,!1),Hn=f(se,"history",9),Ot=f(se,"onExpandAll",9),dn=f(se,"onCollapseAll",9),eo=f(se,"onUndo",9),Tt=f(se,"onRedo",9),Dt=f(se,"onSort",9),ct=f(se,"onTransform",9),Le=f(se,"onContextMenu",9),Ye=f(se,"onCopy",9),vt=f(se,"onRenderMenu",9);function Qt(){tt(!tt())}var ao=E(void 0,!0),yt=E(void 0,!0),xt=E(void 0,!0),In=E(void 0,!0);F(()=>O(ze()),()=>{v(Ie,ze()!==void 0)}),F(()=>(t(Ie),O(Fe()),wt),()=>{v(Oe,t(Ie)&&(Ht(Fe())||Co(Fe())||wt(Fe())))}),F(()=>(O(Ot()),O(ze())),()=>{v(ao,{type:"button",icon:Sx,title:"Expand all",className:"jse-expand-all",onClick:Ot(),disabled:!po(ze())})}),F(()=>(O(dn()),O(ze())),()=>{v(yt,{type:"button",icon:zx,title:"Collapse all",className:"jse-collapse-all",onClick:dn(),disabled:!po(ze())})}),F(()=>O(ze()),()=>{v(xt,{type:"button",icon:Jl,title:"Search (Ctrl+F)",className:"jse-search",onClick:Qt,disabled:ze()===void 0})}),F(()=>(O(wn()),t(ao),t(yt),O(Dt()),O(ze()),O(ct()),t(xt),O(Le()),O(eo()),O(Hn()),O(Tt()),O(Ye()),t(Oe)),()=>{v(In,wn()?[t(ao),t(yt),{type:"separator"},{type:"button",icon:ha,title:"Copy (Ctrl+C)",className:"jse-copy",onClick:Ye(),disabled:!t(Oe)},{type:"separator"},t(xt),{type:"space"}]:[t(ao),t(yt),{type:"separator"},{type:"button",icon:pl,title:"Sort",className:"jse-sort",onClick:Dt(),disabled:wn()||ze()===void 0},{type:"button",icon:hl,title:"Transform contents (filter, sort, project)",className:"jse-transform",onClick:ct(),disabled:wn()||ze()===void 0},t(xt),{type:"button",icon:Vf,title:sd,className:"jse-contextmenu",onClick:Le()},{type:"separator"},{type:"button",icon:Fu,title:"Undo (Ctrl+Z)",className:"jse-undo",onClick:eo(),disabled:!Hn().canUndo},{type:"button",icon:Lu,title:"Redo (Ctrl+Shift+Z)",className:"jse-redo",onClick:Tt(),disabled:!Hn().canRedo},{type:"space"}])}),F(()=>(O(vt()),t(In)),()=>{v(Ce,vt()(t(In))||t(In))}),mt(),jn(!0),sc(J,{get items(){return t(Ce)}}),nn()})(w,{get json(){return t(P)},get selection(){return t(T)},get readOnly(){return g()},get history(){return x()},onExpandAll:Z,onCollapseAll:he,onUndo:He,onRedo:zn,onSort:dt,onTransform:un,onContextMenu:qt,onCopy:Jn,get onRenderMenu(){return Se()},get showSearch(){return t(Xe)},set showSearch(J){v(Xe,J)},$$legacy:!0})};Q(re,w=>{$()&&w(ue)});var xe=q(re,2),Te=w=>{Wx(w,{get json(){return t(P)},get selection(){return t(T)},onSelect:xo,get onError(){return ne()},get pathParser(){return X()}})};Q(xe,w=>{S()&&w(Te)});var Pe=q(xe,2),Ze=w=>{var J=g1(),se=rn(J),Ie=M(se);Ie.readOnly=!0,Yt(Ie,Fe=>v(u,Fe),()=>t(u));var Oe=q(se,2),Ce=Fe=>{var wn=to(),tt=rn(wn),Hn=dn=>{(function(eo,Tt){en(Tt,!0);var Dt=Rx();Dt.__click=[Ex,Tt];var ct=q(M(Dt),2),Le=q(M(ct),2),Ye=vt=>{var Qt=Ax(),ao=q(rn(Qt),2);ht(ao,"title","Create an empty JSON object (press '{')"),ao.__click=[Mx,Tt];var yt=q(ao,2);ht(yt,"title","Create an empty JSON array (press '[')"),yt.__click=[$x,Tt],A(vt,Qt)};Q(Le,vt=>{Tt.readOnly||vt(Ye)}),A(eo,Dt),nn()})(dn,{get readOnly(){return g()},onCreateObject:()=>{$e(),B("{")},onCreateArray:()=>{$e(),B("[")},onClick:()=>{$e()}})},Ot=dn=>{var eo=f1(),Tt=rn(eo),Dt=ae(()=>g()?[]:[{icon:gl,text:"Repair manually",title:'Open the document in "code" mode and repair it manually',onClick:vn}]);jr(Tt,{type:"error",message:"The loaded JSON document is invalid and could not be repaired automatically.",get actions(){return t(Dt)}}),Nh(q(Tt,2),{get text(){return t(pe)},get json(){return t(P)},get indentation(){return H()},get parser(){return N()}}),A(dn,eo)};Q(tt,dn=>{t(pe)===""||t(pe)===void 0?dn(Hn):dn(Ot,!1)}),A(Fe,wn)},ze=Fe=>{var wn=h1(),tt=rn(wn);$h(M(tt),{get json(){return t(P)},get documentState(){return t(V)},get parser(){return N()},get showSearch(){return t(Xe)},get showReplace(){return t($n)},get readOnly(){return g()},columns:void 0,onSearch:De,onFocus:Nn,onPatch:Pn,onClose:qe});var Hn=q(tt,2);ht(Hn,"data-jsoneditor-scrollable-contents",!0);var Ot=M(Hn),dn=Le=>{A(Le,p1())};Q(Ot,Le=>{t(Xe)&&Le(dn)}),zu(q(Ot,2),{get value(){return t(P)},pointer:"",get state(){return t(V)},get validationErrors(){return t(D)},get searchResults(){return t(je)},get selection(){return t(T)},get context(){return t(ro)},onDragSelectionStart:bo}),Yt(Hn,Le=>v(d,Le),()=>t(d));var eo=q(Hn,2),Tt=Le=>{var Ye=ae(()=>"You pasted a JSON ".concat(Array.isArray(t(sn).contents)?"array":"object"," as text"));jr(Le,{type:"info",get message(){return t(Ye)},actions:[{icon:ws,text:"Paste as JSON instead",title:"Replace the value with the pasted JSON",onMouseDown:Bt},{text:"Leave as is",title:"Keep the JSON embedded in the value",onClick:_t}]})};Q(eo,Le=>{t(sn)&&Le(Tt)});var Dt=q(eo,2),ct=Le=>{var Ye=ae(()=>g()?[]:[{icon:Bu,text:"Ok",title:"Accept the repaired document",onClick:yn},{icon:gl,text:"Repair manually instead",title:"Leave the document unchanged and repair it manually instead",onClick:vn}]);jr(Le,{type:"success",message:"The loaded JSON document was invalid but is successfully repaired.",get actions(){return t(Ye)},onClose:$e})};Q(Dt,Le=>{t(gn)&&Le(ct)}),xd(q(Dt,2),{get validationErrors(){return t(Ve)},selectError:Ae}),A(Fe,wn)};Q(Oe,Fe=>{t(P)===void 0?Fe(Ce):Fe(ze,!1)}),ie("paste",Ie,Et),A(w,J)},Bn=w=>{A(w,m1())};Q(Pe,w=>{s?w(Bn,!1):w(Ze)}),Yt(G,w=>v(h,w),()=>t(h));var Zn=q(G,2),Vn=w=>{Sh(w,{onClose:()=>v(I,!1)})};Q(Zn,w=>{t(I)&&w(Vn)});var Cn=q(Zn,2),Rt=w=>{zh(w,ya(()=>t(ee),{onClose:()=>{var J;(J=t(ee))===null||J===void 0||J.onClose(),v(ee,void 0)}}))};return Q(Cn,w=>{t(ee)&&w(Rt)}),be(w=>C=kn(G,1,"jse-tree-mode svelte-vrx1dr",null,C,w),[()=>({"no-main-menu":!$()})],ae),ie("keydown",G,function(w){var J=ta(w),se=w.shiftKey;if(r("keydown",{combo:J,key:w.key}),J==="Ctrl+X"&&(w.preventDefault(),st(!0)),J==="Ctrl+Shift+X"&&(w.preventDefault(),st(!1)),J==="Ctrl+C"&&(w.preventDefault(),Jn(!0)),J==="Ctrl+Shift+C"&&(w.preventDefault(),Jn(!1)),J==="Ctrl+D"&&(w.preventDefault(),jo()),J!=="Delete"&&J!=="Backspace"||(w.preventDefault(),uo()),J==="Insert"&&(w.preventDefault(),Pt("structure")),J==="Ctrl+A"&&(w.preventDefault(),v(T,Yn([]))),J==="Ctrl+Q"&&Ut(w),J==="ArrowUp"||J==="Shift+ArrowUp"){w.preventDefault();var Ie=t(T)?Av(t(P),t(V),t(T),se)||t(T):rs(t(P),t(V));v(T,Ie),At(Be(Ie))}if(J==="ArrowDown"||J==="Shift+ArrowDown"){w.preventDefault();var Oe=t(T)?function(Hn,Ot,dn){var eo=arguments.length>3&&arguments[3]!==void 0&&arguments[3];if(dn){var Tt=eo?Be(dn):ka(Hn,dn),Dt=po(Je(Hn,Tt))?zv(Hn,Ot,Tt,!0):Ot,ct=Ic(Hn,Ot,Tt),Le=Ic(Hn,Dt,Tt);if(eo)return Ro(dn)?ct!==void 0?qo(ct,ct):void 0:or(dn)?Le!==void 0?qo(Le,Le):void 0:Le!==void 0?qo(qa(dn),Le):void 0;if(or(dn))return Le!==void 0?Yn(Le):void 0;if(Ro(dn)||wt(dn))return ct!==void 0?Yn(ct):void 0;if(Co(dn)){if(ct===void 0||ct.length===0)return;var Ye=ot(ct),vt=Je(Hn,Ye);return Array.isArray(vt)?Yn(ct):ra(ct)}return Ht(dn)?Le!==void 0?Yn(Le):ct!==void 0?Yn(ct):void 0:void 0}}(t(P),t(V),t(T),se)||t(T):rs(t(P),t(V));v(T,Oe),At(Be(Oe))}if(J==="ArrowLeft"||J==="Shift+ArrowLeft"){w.preventDefault();var Ce=t(T)?function(Hn,Ot,dn){var eo=arguments.length>3&&arguments[3]!==void 0&&arguments[3],Tt=!(arguments.length>4&&arguments[4]!==void 0)||arguments[4];if(dn){var{caret:Dt,previous:ct}=Rv(Hn,Ot,dn,Tt);if(eo)return Ht(dn)?void 0:qo(dn.path,dn.path);if(Dt&&ct)return mu(ct);var Le=ot(Be(dn)),Ye=Je(Hn,Le);return wt(dn)&&Array.isArray(Ye)?qo(dn.path,dn.path):Ht(dn)&&!Array.isArray(Ye)?ra(dn.focusPath):void 0}}(t(P),t(V),t(T),se,!g())||t(T):rs(t(P),t(V));v(T,Ce),At(Be(Ce))}if(J==="ArrowRight"||J==="Shift+ArrowRight"){w.preventDefault();var ze=t(T)&&t(P)!==void 0?function(Hn,Ot,dn){var eo=arguments.length>3&&arguments[3]!==void 0&&arguments[3],Tt=!(arguments.length>4&&arguments[4]!==void 0)||arguments[4];if(dn){var{caret:Dt,next:ct}=Rv(Hn,Ot,dn,Tt);return eo?Ht(dn)?void 0:qo(dn.path,dn.path):Dt&&ct?mu(ct):Ht(dn)?Yn(dn.focusPath):void 0}}(t(P),t(V),t(T),se,!g())||t(T):rs(t(P),t(V));v(T,ze),At(Be(ze))}if(J==="Enter"&&t(T)){if(tc(t(T))){var Fe=t(T).focusPath,wn=Je(t(P),ot(Fe));Array.isArray(wn)&&(w.preventDefault(),v(T,Yn(Fe)))}Co(t(T))&&(w.preventDefault(),v(T,me(me({},t(T)),{},{edit:!0}))),wt(t(T))&&(w.preventDefault(),po(Je(t(P),t(T).path))?U(t(T).path,!0):v(T,me(me({},t(T)),{},{edit:!0})))}if(J.replace(/^Shift\+/,"").length===1&&t(T))return w.preventDefault(),void B(w.key);if(J==="Enter"&&(or(t(T))||Ro(t(T))))return w.preventDefault(),void B("");if(J==="Ctrl+Enter"&&wt(t(T))){var tt=Je(t(P),t(T).path);Xl(tt)&&window.open(String(tt),"_blank")}J==="Escape"&&t(T)&&(w.preventDefault(),v(T,void 0)),J==="Ctrl+F"&&(w.preventDefault(),An(!1)),J==="Ctrl+H"&&(w.preventDefault(),An(!0)),J==="Ctrl+Z"&&(w.preventDefault(),He()),J==="Ctrl+Shift+Z"&&(w.preventDefault(),zn())}),ie("mousedown",G,function(w){r("handleMouseDown",w);var J=w.target;Wp(J,"BUTTON")||J.isContentEditable||($e(),t(T)||t(P)!==void 0||t(pe)!==""&&t(pe)!==void 0||(r("createDefaultSelection"),v(T,Yn([]))))}),ie("contextmenu",G,Ut),A(e,_o),hn(n,"expand",Ue),hn(n,"collapse",Fn),hn(n,"validate",Ct),hn(n,"getJson",_e),hn(n,"patch",Qn),hn(n,"acceptAutoRepair",yn),hn(n,"openTransformModal",On),hn(n,"scrollTo",En),hn(n,"findElement",pn),hn(n,"findSearchResult",St),hn(n,"focus",$e),nn({expand:Ue,collapse:Fn,validate:Ct,getJson:_e,patch:Qn,acceptAutoRepair:yn,openTransformModal:On,scrollTo:En,findElement:pn,findSearchResult:St,focus:$e})}function _h(e){return typeof(n=e)!="object"||n===null?e:new Proxy(e,{get:(o,r,s)=>_h(Reflect.get(o,r,s)),set:()=>!1,deleteProperty:()=>!1});var n}var ol=So("jsoneditor:History");function Dh(){var e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{},n=e.maxItems||1e3,o=[],r=0;function s(){return r<o.length}function a(){return r>0}function i(){return{canUndo:s(),canRedo:a(),items:()=>o.slice().reverse(),add:c,undo:u,redo:h,clear:d}}function l(){e.onChange&&e.onChange(i())}function c(p){ol("add",p),o=[p].concat(o.slice(r)).slice(0,n),r=0,l()}function d(){ol("clear"),o=[],r=0,l()}function u(){if(s()){var p=o[r];return r+=1,ol("undo",p),l(),p}}function h(){if(a())return ol("redo",o[r-=1]),l(),o[r]}return{get:i}}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-transform-modal-inner.svelte-rrrjnb {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) {
  color: inherit;
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: auto;
  min-width: 0;
  min-height: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-actions:where(.svelte-rrrjnb) {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  padding-top: var(--jse-padding, 10px);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-actions:where(.svelte-rrrjnb) button.jse-primary:where(.svelte-rrrjnb) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-actions:where(.svelte-rrrjnb) button.jse-primary:where(.svelte-rrrjnb):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-actions:where(.svelte-rrrjnb) button.jse-primary:where(.svelte-rrrjnb):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) {
  flex: 1;
  display: flex;
  gap: calc(2 * var(--jse-padding, 10px));
  min-height: 0;
  box-sizing: border-box;
  padding: 0 calc(2 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) .jse-description:where(.svelte-rrrjnb) p {
  margin: var(--jse-padding, 10px) 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) .jse-description:where(.svelte-rrrjnb) p:first-child {
  margin-top: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) .jse-description:where(.svelte-rrrjnb) p:last-child {
  margin-bottom: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) .jse-description:where(.svelte-rrrjnb) code {
  background: var(--jse-modal-code-background, rgba(0, 0, 0, 0.05));
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) .query-error:where(.svelte-rrrjnb) {
  color: var(--jse-error-color, #ee5341);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) textarea.jse-query:where(.svelte-rrrjnb) {
  flex: 1;
  outline: none;
  resize: vertical;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents:where(.svelte-rrrjnb) {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: calc(2 * var(--jse-padding, 10px));
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents:where(.svelte-rrrjnb) .jse-original-data:where(.svelte-rrrjnb) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  box-sizing: border-box;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents:where(.svelte-rrrjnb) .jse-original-data.jse-hide:where(.svelte-rrrjnb) {
  flex: none;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents:where(.svelte-rrrjnb) .jse-preview-data:where(.svelte-rrrjnb) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  box-sizing: border-box;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents.jse-hide-original-data:where(.svelte-rrrjnb) {
  flex-direction: column;
  gap: 0;
  margin-bottom: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-actions:where(.svelte-rrrjnb) {
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px)) calc(2 * var(--jse-padding, 10px));
}
@media screen and (max-width: 1200px) {
  .jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) {
    flex-direction: column;
    overflow: auto;
  }
  .jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-query-contents:where(.svelte-rrrjnb) textarea.jse-query:where(.svelte-rrrjnb) {
    min-height: 150px;
    flex: none;
  }
  .jse-transform-modal-inner.svelte-rrrjnb .jse-modal-contents:where(.svelte-rrrjnb) .jse-main-contents:where(.svelte-rrrjnb) .jse-data-contents:where(.svelte-rrrjnb) .jse-tree-mode {
    height: 300px;
    flex: none;
  }
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-label:where(.svelte-rrrjnb) {
  font-weight: bold;
  display: block;
  box-sizing: border-box;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-label:where(.svelte-rrrjnb) .jse-label-inner:where(.svelte-rrrjnb) {
  margin-top: calc(2 * var(--jse-padding, 10px));
  margin-bottom: calc(0.5 * var(--jse-padding, 10px));
  box-sizing: border-box;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-label:where(.svelte-rrrjnb) .jse-label-inner:where(.svelte-rrrjnb) button:where(.svelte-rrrjnb) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  font-weight: bold;
  padding: 0;
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-tree-mode {
  flex: 1;
  background: var(--jse-input-background-readonly, transparent);
  box-shadow: none;
  box-sizing: border-box;
  --jse-main-border: var(--jse-input-border, 1px solid #d8dbdf);
}
.jse-transform-modal-inner.svelte-rrrjnb input:where(.svelte-rrrjnb),
.jse-transform-modal-inner.svelte-rrrjnb textarea:where(.svelte-rrrjnb) {
  border: var(--jse-input-border, 1px solid #d8dbdf);
  outline: none;
  box-sizing: border-box;
  padding: calc(0.5 * var(--jse-padding, 10px));
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: inherit;
  background: var(--jse-input-background, var(--jse-background-color, #fff));
}
.jse-transform-modal-inner.svelte-rrrjnb input:where(.svelte-rrrjnb):focus,
.jse-transform-modal-inner.svelte-rrrjnb textarea:where(.svelte-rrrjnb):focus {
  border: var(--jse-input-border-focus, 1px solid var(--jse-input-border-focus, var(--jse-theme-color, #3883fa)));
}
.jse-transform-modal-inner.svelte-rrrjnb input:where(.svelte-rrrjnb):read-only,
.jse-transform-modal-inner.svelte-rrrjnb textarea:where(.svelte-rrrjnb):read-only {
  background: var(--jse-input-background-readonly, transparent);
}
.jse-transform-modal-inner.svelte-rrrjnb .jse-preview.jse-error:where(.svelte-rrrjnb) {
  flex: 1;
  background: var(--jse-input-background-readonly, transparent);
  border: var(--jse-input-border, 1px solid #d8dbdf);
  color: var(--jse-error-color, #ee5341);
  padding: calc(0.5 * var(--jse-padding, 10px));
}
.jse-transform-modal-inner.svelte-rrrjnb a {
  color: var(--jse-a-color, #156fc5);
}
.jse-transform-modal-inner.svelte-rrrjnb a:hover {
  color: var(--jse-a-color-highlight, #0f508d);
}`);var ni=Gl(()=>P0),as=Gl(()=>I0),j1=L('<div class="query-error svelte-rrrjnb"> </div>'),x1=L("<!> <!>",1),y1=L('<div class="jse-preview jse-error svelte-rrrjnb"> </div>'),w1=L('<!> <div class="jse-modal-contents svelte-rrrjnb"><div class="jse-main-contents svelte-rrrjnb"><div class="jse-query-contents svelte-rrrjnb"><div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb">Language</div></div> <div class="jse-description svelte-rrrjnb"><!></div> <div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb">Path</div></div> <input class="jse-path svelte-rrrjnb" type="text" readonly="" title="Selected path"> <div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb"><button type="button" class="svelte-rrrjnb"><!> Wizard</button></div></div> <!> <div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb">Query</div></div> <textarea class="jse-query svelte-rrrjnb" spellcheck="false"></textarea></div> <div><div><div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb"><button type="button" class="svelte-rrrjnb"><!> Original</button></div></div> <!></div> <div class="jse-preview-data svelte-rrrjnb"><div class="jse-label svelte-rrrjnb"><div class="jse-label-inner svelte-rrrjnb">Preview</div></div> <!></div></div></div> <div class="jse-actions svelte-rrrjnb"><button type="button" class="jse-primary svelte-rrrjnb">Transform</button></div></div>',1),k1=L('<div class="jse-transform-modal-inner svelte-rrrjnb"><!></div>');function C1(e,n){var o,r,s;en(n,!1);var a=So("jsoneditor:TransformModal"),i=f(n,"id",25,()=>"transform-modal-"+us()),l=f(n,"json",9),c=f(n,"rootPath",25,()=>[]),d=f(n,"indentation",9),u=f(n,"truncateTextSize",9),h=f(n,"escapeControlCharacters",9),p=f(n,"escapeUnicodeCharacters",9),m=f(n,"parser",9),g=f(n,"parseMemoizeOne",9),b=f(n,"validationParser",9),j=f(n,"pathParser",9),x=f(n,"queryLanguages",9),k=f(n,"queryLanguageId",13),$=f(n,"onChangeQueryLanguage",9),S=f(n,"onRenderValue",9),y=f(n,"onRenderMenu",9),R=f(n,"onRenderContextMenu",9),N=f(n,"onClassName",9),_=f(n,"onTransform",9),z=f(n,"onClose",9),W=E(void 0,!0),X=E(Dh({onChange:V=>v(X,V)}).get(),!0),H=E(void 0,!0),ne=E(void 0,!0),ye=E(!1,!0),te="".concat(i(),":").concat(Sn(c())),ve=(o=ni()[te])!==null&&o!==void 0?o:{},Re=E(as().showWizard!==!1,!0),Ee=E(as().showOriginal!==!1,!0),fe=E((r=ve.queryOptions)!==null&&r!==void 0?r:{},!0),Se=E(k()===ve.queryLanguageId&&ve.query?ve.query:"",!0),we=E((s=ve.isManual)!==null&&s!==void 0&&s,!0),Ne=E(void 0,!0),de=E(void 0,!0),oe=E({text:""},!0);function ce(V){var T;return(T=x().find(xn=>xn.id===V))!==null&&T!==void 0?T:x()[0]}function an(V){try{v(fe,V),v(Se,ce(k()).createQuery(t(H),V)),v(Ne,void 0),v(we,!1),a("updateQueryByWizard",{queryOptions:t(fe),query:t(Se),isManual:t(we)})}catch(T){v(Ne,String(T))}}function Mn(V){v(Se,V.target.value),v(we,!0),a("handleChangeQuery",{query:t(Se),isManual:t(we)})}t(we)||an(t(fe)),Po(()=>{var V;(V=t(W))===null||V===void 0||V.focus()});var Y=fl(function(V,T){if(V===void 0)return v(oe,{text:""}),void v(de,"Error: No JSON");if(T.trim()!=="")try{a("previewTransform",{query:T});var xn=ce(k()).executeQuery(V,T,m());v(oe,{json:xn}),v(de,void 0)}catch(Qe){v(oe,{text:""}),v(de,String(Qe))}else v(oe,{json:V})},300);function I(){if(t(H)===void 0)return v(oe,{text:""}),void v(de,"Error: No JSON");try{a("handleTransform",{query:t(Se)});var V=ce(k()).executeQuery(t(H),t(Se),m());_()([{op:"replace",path:Sn(c()),value:V}]),z()()}catch(T){console.error(T),v(oe,{text:""}),v(de,String(T))}}function ee(){v(Re,!t(Re)),as(as().showWizard=t(Re))}function P(){v(Ee,!t(Ee)),as(as().showOriginal=t(Ee))}function pe(V){V.focus()}function K(V){a("handleChangeQueryLanguage",V),k(V),$()(V),an(t(fe))}function Me(){t(ye)?v(ye,!t(ye)):z()()}F(()=>(O(l()),O(c())),()=>{v(H,_h(Je(l(),c())))}),F(()=>t(H),()=>{v(ne,t(H)?{json:t(H)}:{text:""})}),F(()=>(t(H),t(Se)),()=>{Y(t(H),t(Se))}),F(()=>(ni(),t(fe),t(Se),O(k()),t(we)),()=>{ni(ni()[te]={queryOptions:t(fe),query:t(Se),queryLanguageId:k(),isManual:t(we)}),a("store state in memory",te,ni()[te])}),mt(),jn(!0),zi(e,{get onClose(){return z()},className:"jse-transform-modal",get fullscreen(){return t(ye)},children:(V,T)=>{var xn=k1();fu(M(xn),{children:(Qe,cn)=>{var sn=w1(),je=rn(sn);(function(on,Jn){en(Jn,!1);var Wn,Et=f(Jn,"queryLanguages",9),Kt=f(Jn,"queryLanguageId",9),bt=f(Jn,"fullscreen",13),vo=f(Jn,"onChangeQueryLanguage",9),oo=f(Jn,"onClose",9),uo=E(void 0,!0),{openAbsolutePopup:jo,closeAbsolutePopup:fo}=Ma("absolute-popup");function Pt(){var It={queryLanguages:Et(),queryLanguageId:Kt(),onChangeQueryLanguage:Zt=>{fo(Wn),vo()(Zt)}};Wn=jo(mj,It,{offsetTop:-2,offsetLeft:0,anchor:t(uo),closeOnOuterClick:!0})}jn(!0),Pl(on,{title:"Transform",fullScreenButton:!0,get onClose(){return oo()},get fullscreen(){return bt()},set fullscreen(It){bt(It)},$$slots:{actions:(It,Zt)=>{var le,Gn=xj();nt(M(Gn),{data:Ag}),Yt(Gn,B=>v(uo,B),()=>t(uo)),be(B=>le=kn(Gn,1,"jse-config svelte-1kpylsp",null,le,B),[()=>({hide:Et().length<=1})],ae),ie("click",Gn,Pt),A(It,Gn)}},$$legacy:!0}),nn()})(je,{get queryLanguages(){return x()},get queryLanguageId(){return k()},onChangeQueryLanguage:K,get onClose(){return z()},get fullscreen(){return t(ye)},set fullscreen(on){v(ye,on)},$$legacy:!0});var Xe=M(q(je,2)),$n=M(Xe),De=q(M($n),2);Op(M(De),()=>ce(k()).description,!1,!1);var Nn=q(De,4),ge=q(Nn,2),qe=M(ge),Ae=M(qe),Ue=M(Ae),Fn=ae(()=>t(Re)?Ca:fi);nt(Ue,{get data(){return t(Fn)}});var gn=q(ge,2),Ve=on=>{var Jn=to(),Wn=rn(Jn),Et=bt=>{var vo=x1(),oo=rn(vo);pj(oo,{get queryOptions(){return t(fe)},get json(){return t(H)},onChange:an});var uo=q(oo,2),jo=fo=>{var Pt=j1(),It=M(Pt);be(()=>ln(It,t(Ne))),A(fo,Pt)};Q(uo,fo=>{t(Ne)&&fo(jo)}),A(bt,vo)},Kt=bt=>{A(bt,zo("(Only available for arrays, not for objects)"))};Q(Wn,bt=>{Array.isArray(t(H))?bt(Et):bt(Kt,!1)}),A(on,Jn)};Q(gn,on=>{t(Re)&&on(Ve)});var D=q(gn,4);Yt(D,on=>v(W,on),()=>t(W));var Ke,Ge,Ct=q($n,2),_e=M(Ct),Rn=M(_e),Un=M(Rn),co=M(Un),ut=M(co),_n=ae(()=>t(Ee)?Ca:fi);nt(ut,{get data(){return t(_n)}});var Lt=q(Rn,2),Qn=on=>{Ru(on,{get externalContent(){return t(ne)},externalSelection:void 0,get history(){return t(X)},readOnly:!0,get truncateTextSize(){return u()},mainMenuBar:!1,navigationBar:!1,get indentation(){return d()},get escapeControlCharacters(){return h()},get escapeUnicodeCharacters(){return p()},get parser(){return m()},get parseMemoizeOne(){return g()},get onRenderValue(){return S()},get onRenderMenu(){return y()},get onRenderContextMenu(){return R()},onError:console.error,onChange:bo,onChangeMode:bo,onSelect:bo,onUndo:bo,onRedo:bo,onFocus:bo,onBlur:bo,onSortModal:bo,onTransformModal:bo,onJSONEditorModal:bo,get onClassName(){return N()},validator:void 0,get validationParser(){return b()},get pathParser(){return j()}})};Q(Lt,on=>{t(Ee)&&on(Qn)});var ft=q(_e,2),rt=q(M(ft),2),Ln=on=>{Ru(on,{get externalContent(){return t(oe)},externalSelection:void 0,get history(){return t(X)},readOnly:!0,get truncateTextSize(){return u()},mainMenuBar:!1,navigationBar:!1,get indentation(){return d()},get escapeControlCharacters(){return h()},get escapeUnicodeCharacters(){return p()},get parser(){return m()},get parseMemoizeOne(){return g()},get onRenderValue(){return S()},get onRenderMenu(){return y()},get onRenderContextMenu(){return R()},onError:console.error,onChange:bo,onChangeMode:bo,onSelect:bo,onUndo:bo,onRedo:bo,onFocus:bo,onBlur:bo,onSortModal:bo,onTransformModal:bo,onJSONEditorModal:bo,get onClassName(){return N()},validator:void 0,get validationParser(){return b()},get pathParser(){return j()}})},yn=on=>{var Jn=y1(),Wn=M(Jn);be(()=>ln(Wn,t(de))),A(on,Jn)};Q(rt,on=>{t(de)?on(yn,!1):on(Ln)});var st=M(q(Xe,2));$o(()=>ie("click",st,I)),Uo(st,on=>pe==null?void 0:pe(on)),be((on,Jn,Wn)=>{La(Nn,on),La(D,t(Se)),Ke=kn(Ct,1,"jse-data-contents svelte-rrrjnb",null,Ke,Jn),Ge=kn(_e,1,"jse-original-data svelte-rrrjnb",null,Ge,Wn),st.disabled=!!t(de)},[()=>Mt(c())?"(document root)":vr(c()),()=>({"jse-hide-original-data":!t(Ee)}),()=>({"jse-hide":!t(Ee)})],ae),ie("click",Ae,ee),ie("input",D,Mn),ie("click",co,P),A(Qe,sn)},$$slots:{default:!0}}),Uo(xn,(Qe,cn)=>Il==null?void 0:Il(Qe,cn),()=>Me),A(V,xn)},$$slots:{default:!0}}),nn()}function tr(){}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-status-bar.svelte-1ulj7zd {
  background: var(--jse-panel-background, #ebebeb);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  margin: 0;
  border-top: var(--jse-panel-border, var(--jse-main-border, 1px solid #d7d7d7));
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
  display: flex;
  gap: var(--jse-padding, 10px);
}
.jse-status-bar.svelte-1ulj7zd:last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-status-bar.svelte-1ulj7zd .jse-status-bar-info:where(.svelte-1ulj7zd) {
  padding: 2px;
}`);var S1=L('<div class="jse-status-bar-info svelte-1ulj7zd"> </div>'),z1=L('<div class="jse-status-bar-info svelte-1ulj7zd"> </div>'),O1=L('<div class="jse-status-bar-info svelte-1ulj7zd"> </div>'),M1=L('<div class="jse-status-bar svelte-1ulj7zd"><!> <!> <!></div>'),wd=kg.define([{tag:Qs.propertyName,color:"var(--internal-key-color)"},{tag:Qs.number,color:"var(--internal-value-color-number)"},{tag:Qs.bool,color:"var(--internal-value-color-boolean)"},{tag:Qs.string,color:"var(--internal-value-color-string)"},{tag:Qs.keyword,color:"var(--internal-value-color-null)"}]),$1=Uf(wd),E1=wd.style;wd.style=e=>E1(e||[]);var A1=[Cg.fromClass(class{constructor(e){this.view=e,this.indentUnit=Ud(e.state),this.initialPaddingLeft=null,this.isChrome=window==null?void 0:window.navigator.userAgent.includes("Chrome"),this.generate(e.state)}update(e){var n=Ud(e.state);(n!==this.indentUnit||e.docChanged||e.viewportChanged)&&(this.indentUnit=n,this.generate(e.state))}generate(e){var n=new zg;this.initialPaddingLeft?this.addStyleToBuilder(n,e,this.initialPaddingLeft):this.view.requestMeasure({read:o=>{var r=o.contentDOM.querySelector(".cm-line");r&&(this.initialPaddingLeft=window.getComputedStyle(r).getPropertyValue("padding-left"),this.addStyleToBuilder(n,o.state,this.initialPaddingLeft)),this.decorations=n.finish()}}),this.decorations=n.finish()}addStyleToBuilder(e,n,o){var r=this.getVisibleLines(n);for(var s of r){var{numColumns:a,containsTab:i}=this.numColumns(s.text,n.tabSize),l="calc(".concat(a+this.indentUnit,"ch + ").concat(o,")"),c=this.isChrome?"calc(-".concat(a+this.indentUnit,"ch - ").concat(i?1:0,"px)"):"-".concat(a+this.indentUnit,"ch");e.add(s.from,s.from,Sg.line({attributes:{style:"padding-left: ".concat(l,"; text-indent: ").concat(c,";")}}))}}getVisibleLines(e){var n=new Set,o=null;for(var{from:r,to:s}of this.view.visibleRanges)for(var a=r;a<=s;){var i=e.doc.lineAt(a);o!==i&&(n.add(i),o=i),a=i.to+1}return n}numColumns(e,n){var o=0,r=!1;e:for(var s=0;s<e.length;s++)switch(e[s]){case" ":o+=1;continue e;case"	":o+=n-o%n,r=!0;continue e;case"\r":continue e;default:break e}return{numColumns:o,containsTab:r}}},{decorations:e=>e.decorations})];fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-text-mode.svelte-xt61xw {
  --internal-key-color: var(--jse-key-color, #1a1a1a);
  --internal-value-color-number: var(--jse-value-color-number, #ee422e);
  --internal-value-color-boolean: var(--jse-value-color-boolean, #ff8c00);
  --internal-value-color-string: var(--jse-value-color-string, #008000);
  --internal-value-color-null: var(--jse-value-color-null, #004ed0);
  flex: 1;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: var(--jse-background-color, #fff);
}
.jse-text-mode.no-main-menu.svelte-xt61xw {
  border-top: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) {
  flex: 1;
  display: flex;
  position: relative;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  min-height: 0;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw):last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-text-mode.svelte-xt61xw .jse-contents.jse-hidden:where(.svelte-xt61xw) {
  visibility: hidden;
  position: absolute;
  top: 0;
  left: 0;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor {
  flex: 1;
  overflow: hidden;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-scroller {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  line-height: var(--jse-line-height, calc(1em + 4px));
  color: var(--jse-delimiter-color, rgba(0, 0, 0, 0.38));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-gutters {
  background: var(--jse-panel-background, #ebebeb);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  border-right: var(--jse-panel-border, var(--jse-main-border, 1px solid #d7d7d7));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-activeLine,
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-activeLineGutter {
  background: var(--jse-active-line-background-color, rgba(0, 0, 0, 0.06));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-selectionBackground {
  background: var(--jse-selection-background-color, #d3d3d3);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-searchMatch {
  background-color: var(--jse-search-match-color, #ffe665);
  outline: var(--jse-search-match-outline, none);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-searchMatch.cm-searchMatch-selected {
  background-color: var(--jse-search-match-active-color, var(--jse-search-match-color, #ffe665));
  outline: var(--jse-search-match-outline, 2px solid #e0be00);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-selectionMatch {
  background-color: var(--jse-search-match-background-color, rgba(153, 255, 119, 0.5019607843));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-foldPlaceholder {
  background: var(--jse-tag-background, rgba(0, 0, 0, 0.2));
  color: var(--jse-tag-color, var(--jse-text-color-inverse, #fff));
  border: none;
  padding: 0 var(--jse-padding, 10px);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-tooltip {
  font-size: var(--jse-font-size, 16px);
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  color: var(--jse-tooltip-color, var(--jse-text-color, #4d4d4d));
  background: var(--jse-tooltip-background, var(--jse-modal-background, #f5f5f5));
  border: var(--jse-tooltip-border, var(--jse-main-border, 1px solid #d7d7d7));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-diagnosticAction {
  background: var(--jse-tooltip-action-button-color, var(--jse-text-color-inverse, #fff));
  background: var(--jse-tooltip-action-button-background, #4d4d4d);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-panels {
  border-bottom: var(--jse-panel-border, var(--jse-main-border, 1px solid #d7d7d7));
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search {
  background: var(--jse-panel-background, #ebebeb);
  color: var(--jse-panel-color, var(--jse-text-color, #4d4d4d));
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search input {
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size-text-mode-search, 80%);
  color: var(--jse-input-color, var(--jse-text-color, #4d4d4d));
  border: var(--jse-input-border, 1px solid #d8dbdf);
  background: var(--jse-input-background, var(--jse-background-color, #fff));
  margin-right: 2px;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search button {
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size-text-mode-search, 80%);
  color: var(--jse-panel-button-color, inherit);
  background: var(--jse-panel-button-background, transparent);
  border: none;
  cursor: pointer;
  text-transform: capitalize;
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px);
  margin: 0;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search button:hover {
  color: var(--panel-button-color-highlight, var(--jse-text-color, #4d4d4d));
  background: var(--jse-panel-button-background-highlight, #e0e0e0);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search label {
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size-text-mode-search, 80%);
  padding-left: var(--jse-padding, 10px);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search label input {
  margin-right: 2px;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-search button[name="close"] {
  width: 32px;
  height: 32px;
  font-size: 24px;
  line-height: 24px;
  padding: 0;
  right: 0;
  top: -4px;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .cm-editor .cm-cursor-primary {
  border-color: var(--jse-text-color, #4d4d4d);
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .jse-loading-space:where(.svelte-xt61xw) {
  flex: 1;
}
.jse-text-mode.svelte-xt61xw .jse-contents:where(.svelte-xt61xw) .jse-loading:where(.svelte-xt61xw) {
  flex: 2;
  text-align: center;
  color: var(--jse-panel-color-readonly, #b2b2b2);
  box-sizing: border-box;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
}
.jse-text-mode.svelte-xt61xw .jse-contents.jse-preview:where(.svelte-xt61xw) {
  flex: 1;
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: var(--jse-panel-color-readonly, #b2b2b2);
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 2px;
}`);var R1=L('<!> <div class="jse-contents jse-preview svelte-xt61xw"> </div>',1),T1=L("<!> <!> <!> <!>",1),P1=L("<div></div> <!> <!>",1),I1=L('<div class="jse-contents svelte-xt61xw"><div class="jse-loading-space svelte-xt61xw"></div> <div class="jse-loading svelte-xt61xw">loading...</div></div>'),q1=L("<div><!> <!></div>");function N1(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=f(n,"readOnly",9),a=f(n,"mainMenuBar",9),i=f(n,"statusBar",9),l=f(n,"askToFormat",9),c=f(n,"externalContent",9),d=f(n,"externalSelection",9),u=f(n,"history",9),h=f(n,"indentation",9),p=f(n,"tabSize",9),m=f(n,"escapeUnicodeCharacters",9),g=f(n,"parser",9),b=f(n,"validator",9),j=f(n,"validationParser",9),x=f(n,"onChange",9),k=f(n,"onChangeMode",9),$=f(n,"onSelect",9),S=f(n,"onUndo",9),y=f(n,"onRedo",9),R=f(n,"onError",9),N=f(n,"onFocus",9),_=f(n,"onBlur",9),z=f(n,"onRenderMenu",9),W=f(n,"onSortModal",9),X=f(n,"onTransformModal",9),H=So("jsoneditor:TextMode"),ne={key:"Mod-i",run:cn,shift:sn,preventDefault:!0},ye=typeof window>"u";H("isSSR:",ye);var te,ve=E(void 0,!0),Re=E(void 0,!0),Ee=E(void 0,!0),fe=E(!1,!0),Se=E(l(),!0),we=E([],!0),Ne=new Gs,de=new Gs,oe=new Gs,ce=new Gs,an=new Gs,Mn=c(),Y=E(du(Mn,h(),g()),!0),I=cm.define(),ee=null;function P(){if(!ee||ee.length===0)return!1;var B=ee[0].startState,ke=ee[ee.length-1].state,He=ee.map(We=>We.changes).reduce((We,Tn)=>We.compose(Tn)),zn={type:"text",undo:{changes:He.invert(B.doc).toJSON(),selection:on(B.selection)},redo:{changes:He.toJSON(),selection:on(ke.selection)}};return H("add history item",zn),u().add(zn),ee=null,!0}var pe=E(m(),!0);Po(bn(function*(){if(!ye)try{te=function(B){var{target:ke,initialText:He,readOnly:zn,indentation:We}=B;H("Create CodeMirror editor",{readOnly:zn,indentation:We});var Tn=function(On,pt){return Nc(On)?On.ranges.every(un=>un.anchor<pt.length&&un.head<pt.length):!1}(d(),He)?co(d()):void 0,dt=ns.create({doc:He,selection:Tn,extensions:[_d.of([Rg,ne]),Ne.of(Ke()),Tg(),Pg(),Ig(),qg(),Ng(),Ug(),_g(),ns.allowMultipleSelections.of(!0),Dg(),Uf(Fg,{fallback:!0}),Lg(),Jg(),Bg(),Wg(),Vg(),Hg(),Yg(),_d.of([...Kg,...Qg,...Gg,{key:"Mod-z",run:ge,preventDefault:!0},{key:"Mod-y",mac:"Mod-Shift-z",run:qe,preventDefault:!0},{key:"Ctrl-Shift-z",run:qe,preventDefault:!0},...Xg,...Zg,...em]),$1,nm({hideFirstIndent:!0}),ts.domEventHandlers({dblclick:D}),ts.updateListener.of(On=>{v(Ee,On.state),On.docChanged&&(On.transactions.some(pt=>!!pt.annotation(I))||(ee=[...ee??[],On]),rt()),On.selectionSet&&st()}),tm(),om({top:!0}),ts.lineWrapping,de.of(ns.readOnly.of(zn)),ce.of(ns.tabSize.of(p())),oe.of(ft(We)),an.of(ts.theme({},{dark:Ge()}))]});return te=new ts({state:dt,parent:ke}),Tn&&te.dispatch(te.state.update({selection:Tn.main,scrollIntoView:!0})),te}({target:t(ve),initialText:Jn(t(Y),t(fe))?"":t(o).escapeValue(t(Y)),readOnly:s(),indentation:h()})}catch(B){console.error(B)}})),rr(()=>{Ln(),te&&(H("Destroy CodeMirror editor"),te.destroy())});var K=ys(),Me=ys();function V(){te&&(H("focus"),te.focus())}var T=!1;function xn(B){return Qe(B,!1)}function Qe(B,ke){H("handlePatch",B,ke);var He=g().parse(t(Y)),zn=zr(He,B),We=Ff(He,B);return Un({text:g().stringify(zn,null,h())},ke,!1),{json:zn,previousJson:He,undo:We,redo:B}}function cn(){if(H("format"),s())return!1;try{var B=g().parse(t(Y));return Un({text:g().stringify(B,null,h())},!0,!1),v(Se,l()),!0}catch(ke){R()(ke)}return!1}function sn(){if(H("compact"),s())return!1;try{var B=g().parse(t(Y));return Un({text:g().stringify(B)},!0,!1),v(Se,!1),!0}catch(ke){R()(ke)}return!1}function je(){if(H("repair"),!s())try{Un({text:Or(t(Y))},!0,!1),v(Wn,Tc),v(Et,void 0)}catch(B){R()(B)}}function Xe(){var B;if(!s())try{var ke=g().parse(t(Y));T=!0,W()({id:K,json:ke,rootPath:[],onSort:(B=bn(function*(He){var{operations:zn}=He;H("onSort",zn),Qe(zn,!0)}),function(He){return B.apply(this,arguments)}),onClose:()=>{T=!1,V()}})}catch(He){R()(He)}}function $n(B){var{id:ke,rootPath:He,onTransform:zn,onClose:We}=B;try{var Tn=g().parse(t(Y));T=!0,X()({id:ke||Me,json:Tn,rootPath:He||[],onTransform:dt=>{zn?zn({operations:dt,json:Tn,transformedJson:zr(Tn,dt)}):(H("onTransform",dt),Qe(dt,!0))},onClose:()=>{T=!1,V(),We&&We()}})}catch(dt){R()(dt)}}function De(){s()||$n({rootPath:[]})}function Nn(){te&&(t(ve)&&t(ve).querySelector(".cm-search")?bm(te):jm(te))}function ge(){if(s())return!1;Ln();var B=u().undo();return H("undo",B),Cv(B)?(te.dispatch({annotations:I.of("undo"),changes:Dd.fromJSON(B.undo.changes),selection:mc.fromJSON(B.undo.selection),scrollIntoView:!0}),!0):(S()(B),!1)}function qe(){if(s())return!1;Ln();var B=u().redo();return H("redo",B),Cv(B)?(te.dispatch({annotations:I.of("redo"),changes:Dd.fromJSON(B.redo.changes),selection:mc.fromJSON(B.redo.selection),scrollIntoView:!0}),!0):(y()(B),!1)}function Ae(){v(fe,!0),Un(c(),!0,!0)}function Ue(){k()(yo.tree)}function Fn(){Lt()}function gn(B){H("select validation error",B);var{from:ke,to:He}=Ct(B);ke!==void 0&&He!==void 0&&(Ve(ke,He),V())}function Ve(B,ke){H("setSelection",{anchor:B,head:ke}),te&&te.dispatch(te.state.update({selection:{anchor:B,head:ke},scrollIntoView:!0}))}function D(B,ke){if(ke.state.selection.ranges.length===1){var He=ke.state.selection.ranges[0],zn=t(Y).slice(He.from,He.to);if(zn==="{"||zn==="["){var We=Fd.parse(t(Y)),Tn=Object.keys(We.pointers).find(On=>{var pt;return((pt=We.pointers[On].value)===null||pt===void 0?void 0:pt.pos)===He.from}),dt=We.pointers[Tn];Tn&&dt&&dt.value&&dt.valueEnd&&(H("pointer found, selecting inner contents of path:",Tn,dt),Ve(dt.value.pos+1,dt.valueEnd.pos-1))}}}function Ke(){return rm(Kt,{delay:300})}function Ge(){return!!t(ve)&&getComputedStyle(t(ve)).getPropertyValue("--jse-theme").includes("dark")}function Ct(B){var{path:ke,message:He,severity:zn}=B,{line:We,column:Tn,from:dt,to:On}=function(pt,un){try{var En=Fd.parse(pt),tn=Sn(un),pn=En.pointers[tn];if(pn)return{path:un,line:pn.key?pn.key.line:pn.value?pn.value.line:0,column:pn.key?pn.key.column:pn.value?pn.value.column:0,from:pn.key?pn.key.pos:pn.value?pn.value.pos:0,to:pn.keyEnd?pn.keyEnd.pos:pn.valueEnd?pn.valueEnd.pos:0}}catch(St){console.error(St)}return{path:un,line:0,column:0,from:0,to:0}}(t(o).escapeValue(t(Y)),ke);return{path:ke,line:We,column:Tn,from:dt,to:On,message:He,severity:zn,actions:[]}}function _e(B,ke){var{line:He,column:zn,position:We,message:Tn}=B;return{path:[],line:He,column:zn,from:We,to:We,severity:pr.error,message:Tn,actions:ke&&!s()?[{name:"Auto repair",apply:()=>je()}]:void 0}}function Rn(B){return{from:B.from||0,to:B.to||0,message:B.message||"",actions:B.actions,severity:B.severity}}function Un(B,ke,He){var zn=du(B,h(),g()),We=!et(B,Mn),Tn=Mn;H("setCodeMirrorContent",{isChanged:We,emitChange:ke,forceUpdate:He}),te&&(We||He)&&(Mn=B,v(Y,zn),Jn(t(Y),t(fe))||te.dispatch({changes:{from:0,to:te.state.doc.length,insert:t(o).escapeValue(t(Y))}}),P(),We&&ke&&yn(Mn,Tn))}function co(B){return Nc(B)?mc.fromJSON(B):void 0}function ut(){return _n.apply(this,arguments)}function _n(){return _n=bn(function*(){H("refresh"),yield function(){return Qn.apply(this,arguments)}()}),_n.apply(this,arguments)}function Lt(){if(te){var B=te?t(o).unescapeValue(te.state.doc.toString()):"",ke=B!==t(Y);if(H("onChangeCodeMirrorValue",{isChanged:ke}),ke){var He=Mn;v(Y,B),Mn={text:t(Y)},P(),yn(Mn,He),Gt(),st()}}}function Qn(){return(Qn=bn(function*(){if(Gt(),te){var B=Ge();return H("updateTheme",{dark:B}),te.dispatch({effects:[an.reconfigure(ts.theme({},{dark:B}))]}),new Promise(ke=>setTimeout(ke))}return Promise.resolve()})).apply(this,arguments)}function ft(B){var ke=am.of(typeof B=="number"?" ".repeat(B):B);return B==="	"?[ke]:[ke,A1]}jd({onMount:Po,onDestroy:rr,getWindow:()=>Ni(t(Re)),hasFocus:()=>T&&document.hasFocus()||od(t(Re)),onFocus:N(),onBlur:()=>{Ln(),_()()}});var rt=fl(Lt,300);function Ln(){rt.flush()}function yn(B,ke){x()&&x()(B,ke,{contentErrors:bt(),patchResult:void 0})}function st(){$()(on(t(Ee).selection))}function on(B){return me({type:$t.text},B.toJSON())}function Jn(B,ke){return!!B&&B.length>bv&&!ke}var Wn=E(Tc,!0),Et=E(void 0,!0);function Kt(){if(Jn(t(Y),t(fe)))return[];var B=bt();if(wv(B)){var{parseError:ke,isRepairable:He}=B;return[Rn(_e(ke,He))]}return v0(B)?B.validationErrors.map(Ct).map(Rn):[]}function bt(){H("validate:start"),Ln();var B=vo(t(o).escapeValue(t(Y)),b(),g(),j());return wv(B)?(v(Wn,B.isRepairable?xv:"invalid"),v(Et,B.parseError),v(we,[])):(v(Wn,Tc),v(Et,void 0),v(we,(B==null?void 0:B.validationErrors)||[])),H("validate:end"),B}var vo=vi(kj);function oo(){t(Et)&&function(B){H("select parse error",B);var ke=_e(B,!1);Ve(ke.from!=null?ke.from:0,ke.to!=null?ke.to:0),V()}(t(Et))}var uo={icon:um,text:"Show me",title:"Move to the parse error location",onClick:oo};F(()=>O(m()),()=>{v(o,nd({escapeControlCharacters:!1,escapeUnicodeCharacters:m()}))}),F(()=>O(c()),()=>{Un(c(),!1,!1)}),F(()=>O(d()),()=>{(function(B){if(Nc(B)){var ke=co(B);!te||!ke||t(Ee)&&t(Ee).selection.eq(ke)||(H("applyExternalSelection",ke),te.dispatch({selection:ke}))}})(d())}),F(()=>O(b()),()=>{(function(B){H("updateLinter",B),te&&te.dispatch({effects:Ne.reconfigure(Ke())})})(b())}),F(()=>O(h()),()=>{(function(B){te&&(H("updateIndentation",B),te.dispatch({effects:oe.reconfigure(ft(B))}))})(h())}),F(()=>O(p()),()=>{(function(B){te&&(H("updateTabSize",B),te.dispatch({effects:ce.reconfigure(ns.tabSize.of(B))}))})(p())}),F(()=>O(s()),()=>{(function(B){te&&(H("updateReadOnly",B),te.dispatch({effects:[de.reconfigure(ns.readOnly.of(B))]}))})(s())}),F(()=>(t(pe),O(m())),()=>{t(pe)!==m()&&(v(pe,m()),H("forceUpdateText",{escapeUnicodeCharacters:m()}),te&&te.dispatch({changes:{from:0,to:te.state.doc.length,insert:t(o).escapeValue(t(Y))}}))}),F(()=>(t(Wn),O(s()),ws),()=>{v(r,t(Wn)!==xv||s()?[uo]:[{icon:ws,text:"Auto repair",title:"Automatically repair JSON",onClick:je},uo])}),mt(),jn(!0);var jo,fo=q1(),Pt=M(fo),It=B=>{var ke=ae(()=>t(Y).length===0),He=ae(()=>!t(ke)),zn=ae(()=>!t(ke)),We=ae(()=>!t(ke)),Tn=ae(()=>!t(ke));(function(dt,On){en(On,!1);var pt=E(void 0,!0),un=f(On,"readOnly",9,!1),En=f(On,"onFormat",9),tn=f(On,"onCompact",9),pn=f(On,"onSort",9),St=f(On,"onTransform",9),At=f(On,"onToggleSearch",9),Dn=f(On,"onUndo",9),Pn=f(On,"onRedo",9),qn=f(On,"canUndo",9),Jt=f(On,"canRedo",9),U=f(On,"canFormat",9),Z=f(On,"canCompact",9),he=f(On,"canSort",9),An=f(On,"canTransform",9),it=f(On,"onRenderMenu",9),Xn={type:"button",icon:Jl,title:"Search (Ctrl+F)",className:"jse-search",onClick:At()},jt=E(void 0,!0);F(()=>(O(un()),O(En()),O(U()),O(tn()),O(Z()),O(pn()),O(he()),O(St()),O(An()),O(Dn()),O(qn()),O(Pn()),O(Jt())),()=>{v(jt,un()?[Xn,{type:"space"}]:[{type:"button",icon:Qv,title:"Format JSON: add proper indentation and new lines (Ctrl+I)",className:"jse-format",onClick:En(),disabled:un()||!U()},{type:"button",icon:Ox,title:"Compact JSON: remove all white spacing and new lines (Ctrl+Shift+I)",className:"jse-compact",onClick:tn(),disabled:un()||!Z()},{type:"separator"},{type:"button",icon:pl,title:"Sort",className:"jse-sort",onClick:pn(),disabled:un()||!he()},{type:"button",icon:hl,title:"Transform contents (filter, sort, project)",className:"jse-transform",onClick:St(),disabled:un()||!An()},Xn,{type:"separator"},{type:"button",icon:Fu,title:"Undo (Ctrl+Z)",className:"jse-undo",onClick:Dn(),disabled:!qn()},{type:"button",icon:Lu,title:"Redo (Ctrl+Shift+Z)",className:"jse-redo",onClick:Pn(),disabled:!Jt()},{type:"space"}])}),F(()=>(O(it()),t(jt)),()=>{v(pt,it()(t(jt))||t(jt))}),mt(),jn(!0),sc(dt,{get items(){return t(pt)}}),nn()})(B,{get readOnly(){return s()},onFormat:cn,onCompact:sn,onSort:Xe,onTransform:De,onToggleSearch:Nn,onUndo:ge,onRedo:qe,get canFormat(){return t(He)},get canCompact(){return t(zn)},get canSort(){return t(We)},get canTransform(){return t(Tn)},get canUndo(){return u().canUndo},get canRedo(){return u().canRedo},get onRenderMenu(){return z()}})};Q(Pt,B=>{a()&&B(It)});var Zt=q(Pt,2),le=B=>{var ke,He=P1(),zn=ae(()=>Jn(t(Y),t(fe))),We=rn(He);Yt(We,un=>v(ve,un),()=>t(ve));var Tn=q(We,2),dt=un=>{var En=R1(),tn=rn(En),pn=ae(()=>"The JSON document is larger than ".concat(yu(bv),", ")+"and may crash your browser when loading it in text mode. Actual size: ".concat(yu(t(Y).length),"."));jr(tn,{icon:_a,type:"error",get message(){return t(pn)},actions:[{text:"Open anyway",title:"Open the document in text mode. This may freeze or crash your browser.",onClick:Ae},{text:"Open in tree mode",title:"Open the document in tree mode. Tree mode can handle large documents.",onClick:Ue},{text:"Cancel",title:"Cancel opening this large document.",onClick:Fn}],onClose:V});var St=M(q(tn,2));be(At=>ln(St,At),[()=>bi(t(Y)||"",pu)],ae),A(un,En)};Q(Tn,un=>{t(zn)&&un(dt)});var On=q(Tn,2),pt=un=>{var En=T1(),tn=rn(En),pn=qn=>{(function(Jt,U){en(U,!1);var Z=f(U,"editorState",8),he=E(),An=E(),it=E(),Xn=E(),jt=E();F(()=>O(Z()),()=>{var $e;v(he,($e=Z())===null||$e===void 0||($e=$e.selection)===null||$e===void 0||($e=$e.main)===null||$e===void 0?void 0:$e.head)}),F(()=>(t(he),O(Z())),()=>{var $e;v(An,t(he)!==void 0?($e=Z())===null||$e===void 0||($e=$e.doc)===null||$e===void 0?void 0:$e.lineAt(t(he)):void 0)}),F(()=>t(An),()=>{v(it,t(An)!==void 0?t(An).number:void 0)}),F(()=>(t(An),t(he)),()=>{v(Xn,t(An)!==void 0&&t(he)!==void 0?t(he)-t(An).from+1:void 0)}),F(()=>O(Z()),()=>{var $e;v(jt,($e=Z())===null||$e===void 0||($e=$e.selection)===null||$e===void 0||($e=$e.ranges)===null||$e===void 0?void 0:$e.reduce((at,zt)=>at+zt.to-zt.from,0))}),mt(),jn();var Ut=M1(),qt=M(Ut),Bt=$e=>{var at=S1(),zt=M(at);be(()=>{var lt;return ln(zt,"Line: ".concat((lt=t(it))!==null&&lt!==void 0?lt:""))}),A($e,at)};Q(qt,$e=>{t(it)!==void 0&&$e(Bt)});var mn=q(qt,2),_t=$e=>{var at=z1(),zt=M(at);be(()=>{var lt;return ln(zt,"Column: ".concat((lt=t(Xn))!==null&&lt!==void 0?lt:""))}),A($e,at)};Q(mn,$e=>{t(Xn)!==void 0&&$e(_t)});var vn=q(mn,2),xo=$e=>{var at=O1(),zt=M(at);be(()=>{var lt;return ln(zt,"Selection: ".concat((lt=t(jt))!==null&&lt!==void 0?lt:""," characters"))}),A($e,at)};Q(vn,$e=>{t(jt)!==void 0&&t(jt)>0&&$e(xo)}),A(Jt,Ut),nn()})(qn,{get editorState(){return t(Ee)}})};Q(tn,qn=>{i()&&qn(pn)});var St=q(tn,2),At=qn=>{jr(qn,{type:"error",icon:_a,get message(){return t(Et).message},get actions(){return t(r)},onClick:oo,onClose:V})};Q(St,qn=>{t(Et)&&qn(At)});var Dn=q(St,2),Pn=qn=>{jr(qn,{type:"success",message:"Do you want to format the JSON?",actions:[{icon:Qv,text:"Format",title:"Format JSON: add proper indentation and new lines (Ctrl+I)",onClick:cn},{icon:Ll,text:"No thanks",title:"Close this message",onClick:()=>v(Se,!1)}],onClose:V})};Q(Dn,qn=>{var Jt,U;!t(Et)&&t(Se)&&(Jt=t(Y),!(U=Jt.substring(0,999).trim()).includes(`
`)&&qb.test(U))&&qn(Pn)}),xd(q(Dn,2),{get validationErrors(){return t(we)},selectError:gn}),A(un,En)};Q(On,un=>{t(zn)||un(pt)}),be(un=>ke=kn(We,1,"jse-contents svelte-xt61xw",null,ke,un),[()=>({"jse-hidden":t(zn)})],ae),A(B,He)},Gn=B=>{A(B,I1())};return Q(Zt,B=>{ye?B(Gn,!1):B(le)}),Yt(fo,B=>v(Re,B),()=>t(Re)),be(B=>jo=kn(fo,1,"jse-text-mode svelte-xt61xw",null,jo,B),[()=>({"no-main-menu":!a()})],ae),A(e,fo),hn(n,"focus",V),hn(n,"patch",xn),hn(n,"handlePatch",Qe),hn(n,"openTransformModal",$n),hn(n,"refresh",ut),hn(n,"flush",Ln),hn(n,"validate",bt),nn({focus:V,patch:xn,handlePatch:Qe,openTransformModal:$n,refresh:ut,flush:Ln,validate:bt})}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-inline-value.svelte-h57m0p {
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  line-height: var(--jse-line-height, calc(1em + 4px));
  border: none;
  padding: 0 calc(0.5 * var(--jse-padding, 10px));
  background: transparent;
  color: inherit;
  cursor: inherit;
}
.jse-inline-value.jse-highlight.svelte-h57m0p {
  background-color: var(--jse-search-match-color, #ffe665);
  outline: var(--jse-search-match-outline, none);
}
.jse-inline-value.jse-highlight.jse-active.svelte-h57m0p {
  background-color: var(--jse-search-match-active-color, var(--jse-search-match-color, #ffe665));
  outline: var(--jse-search-match-outline, 2px solid #e0be00);
}`);var U1=L('<button type="button"> </button>');fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-column-header.svelte-2i3vdx {
  background: none;
  border: none;
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  display: flex;
  gap: var(--jse-padding, 10px);
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px) calc(0.5 * var(--jse-padding, 10px)) calc(0.5 * var(--jse-padding, 10px));
  width: 100%;
}
.jse-column-header.svelte-2i3vdx:hover {
  background: var(--jse-table-header-background-highlight, #e8e8e8);
}
.jse-column-header.svelte-2i3vdx:not(.jse-column-header.jse-readonly) {
  cursor: pointer;
}
.jse-column-header.svelte-2i3vdx span.jse-column-sort-icon:where(.svelte-2i3vdx) {
  height: 1em;
}`);var _1=L('<span class="jse-column-sort-icon svelte-2i3vdx"><!></span>'),D1=L('<button type="button"><span class="jse-column-name"> </span> <!></button>');fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-table-mode-welcome.svelte-17xl1jx {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
  align-items: center;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-table-mode-welcome.svelte-17xl1jx:last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-space.jse-before:where(.svelte-17xl1jx) {
  flex: 1;
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) {
  display: flex;
  flex-direction: column;
  gap: var(--jse-padding, 10px);
  max-width: 400px;
  margin: 2em var(--jse-padding, 10px);
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) .jse-nested-arrays-info:where(.svelte-17xl1jx) {
  color: var(--jse-panel-color-readonly, #b2b2b2);
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) .jse-nested-property:where(.svelte-17xl1jx) {
  display: flex;
  align-items: center;
  gap: var(--jse-padding, 10px);
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) .jse-nested-property:where(.svelte-17xl1jx) .jse-nested-property-path:where(.svelte-17xl1jx) {
  flex: 1;
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) .jse-nested-property:where(.svelte-17xl1jx) .jse-nested-property-path:where(.svelte-17xl1jx) .jse-nested-property-count:where(.svelte-17xl1jx) {
  opacity: 0.5;
  white-space: nowrap;
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) button.jse-nested-array-action:where(.svelte-17xl1jx) {
  text-align: left;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) button.jse-nested-array-action:where(.svelte-17xl1jx):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-nested-arrays:where(.svelte-17xl1jx) button.jse-nested-array-action:where(.svelte-17xl1jx):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}
.jse-table-mode-welcome.svelte-17xl1jx .jse-space.jse-after:where(.svelte-17xl1jx) {
  flex: 2;
}`);var F1=(e,n)=>n.onClick(),L1=L(`An empty document cannot be opened in table mode. You can go to tree mode instead, or paste
        a JSON Array using <b>Ctrl+V</b>.`,1),J1=(e,n,o)=>n.openJSONEditorModal(t(o)),B1=(e,n,o)=>n.extractPath(t(o)),W1=L('<button type="button" class="jse-nested-array-action svelte-17xl1jx">Extract</button>'),V1=L('<div class="jse-nested-property svelte-17xl1jx"><div class="jse-nested-property-path svelte-17xl1jx"> <span class="jse-nested-property-count svelte-17xl1jx"> </span></div> <button type="button" class="jse-nested-array-action svelte-17xl1jx"> </button> <!></div>'),H1=(e,n)=>n.onChangeMode(yo.tree),Y1=L('<div class="jse-table-mode-welcome svelte-17xl1jx" role="none"><div class="jse-space jse-before svelte-17xl1jx"></div> <div class="jse-nested-arrays svelte-17xl1jx"><div class="jse-nested-arrays-title"> </div> <div class="jse-nested-arrays-info svelte-17xl1jx"><!></div> <!> <button type="button" class="jse-nested-array-action svelte-17xl1jx">Switch to tree mode</button></div> <div class="jse-space jse-after svelte-17xl1jx"></div></div>');function K1(e,n){en(n,!0);var o=Wo(()=>n.json?function(b){var j=arguments.length>1&&arguments[1]!==void 0?arguments[1]:2,x=[];return function k($,S){Oo($)&&S.length<j&&Object.keys($).forEach(y=>{k($[y],S.concat(y))}),wo($)&&x.push(S)}(b,[]),x}(n.json).slice(0,99).filter(b=>b.length>0):[]),r=Wo(()=>!Mt(t(o))),s=Wo(()=>n.json===void 0&&(n.text===""||n.text===void 0)),a=Wo(()=>t(r)?"Object with nested arrays":t(s)?"An empty document":Oo(n.json)?"An object":wo(n.json)?"An empty array":"A ".concat(ed(n.json,n.parser))),i=Y1();i.__click=[F1,n];var l=q(M(i),2),c=M(l),d=M(c),u=q(c,2),h=M(u),p=b=>{A(b,zo(`An object cannot be opened in table mode. You can open a nested array instead, or open the
        document in tree mode.`))},m=(b,j)=>{var x=$=>{A($,L1())},k=$=>{var S=zo();be(()=>{var y;return ln(S,"".concat((y=t(a))!==null&&y!==void 0?y:""," cannot be opened in table mode. You can open the document in tree mode instead."))}),A($,S)};Q(b,$=>{t(s)&&!n.readOnly?$(x):$(k,!1)},j)};Q(h,b=>{t(r)?b(p):b(m,!1)});var g=q(u,2);lo(g,17,()=>t(o),ho,(b,j)=>{var x=V1(),k=Wo(()=>function(W){return Je(n.json,W).length}(t(j))),$=M(x),S=M($),y=M(q(S)),R=q($,2);R.__click=[J1,n,j];var N=M(R),_=q(R,2),z=W=>{var X=W1();X.__click=[B1,n,j],A(W,X)};Q(_,W=>{n.readOnly||W(z)}),be(W=>{var X,H;ln(S,'"'.concat(W??"",'" ')),ln(y,"(".concat((X=t(k))!==null&&X!==void 0?X:""," ").concat((H=t(k)!==1?"items":"item")!==null&&H!==void 0?H:"",")")),ln(N,n.readOnly?"View":"Edit")},[()=>vr(t(j))]),A(b,x)}),q(g,2).__click=[H1,n],be(()=>ln(d,t(a))),A(e,i),nn()}Ii(["click"]);fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-column-header.svelte-fzj761 {
  background: none;
  border: none;
  font-family: inherit;
  font-size: inherit;
  color: inherit;
  display: flex;
  gap: var(--jse-padding, 10px);
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px) calc(0.5 * var(--jse-padding, 10px)) calc(0.5 * var(--jse-padding, 10px));
  width: 100%;
}
.jse-column-header.svelte-fzj761:hover {
  background: var(--jse-table-header-background-highlight, #e8e8e8);
}
.jse-column-header.svelte-fzj761:not(.jse-column-header.jse-readonly) {
  cursor: pointer;
}`);var Q1=L('<button type="button"><!></button>');fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-table-mode.svelte-u14cgx {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--jse-background-color, #fff);
  min-width: 0;
  min-height: 0;
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: var(--jse-text-color, #4d4d4d);
  line-height: var(--jse-line-height, calc(1em + 4px));
}
.jse-table-mode.no-main-menu.svelte-u14cgx {
  border-top: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-table-mode.svelte-u14cgx .jse-search-box-container:where(.svelte-u14cgx) {
  position: relative;
  height: 0;
  top: calc(var(--jse-line-height, calc(1em + 4px)) + 2 * var(--jse-padding, 10px));
  margin-right: calc(var(--jse-padding, 10px) + 20px);
  margin-left: var(--jse-padding, 10px);
  text-align: right;
  z-index: 3;
}
.jse-table-mode.svelte-u14cgx .jse-hidden-input-label:where(.svelte-u14cgx) {
  position: fixed;
  right: 0;
  top: 0;
  width: 0;
  height: 0;
}
.jse-table-mode.svelte-u14cgx .jse-hidden-input-label:where(.svelte-u14cgx) .jse-hidden-input:where(.svelte-u14cgx) {
  width: 0;
  height: 0;
  padding: 0;
  border: 0;
  outline: none;
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) {
  flex: 1;
  align-items: flex-start;
  flex-direction: column;
  display: flex;
  overflow: auto;
  overflow-anchor: none;
  scrollbar-gutter: stable;
  border-left: var(--jse-main-border, 1px solid #d7d7d7);
  border-right: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx):last-child {
  border-bottom: var(--jse-main-border, 1px solid #d7d7d7);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) {
  border-collapse: collapse;
  border-spacing: 0;
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-invisible-start-section:where(.svelte-u14cgx) td:where(.svelte-u14cgx),
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-invisible-end-section:where(.svelte-u14cgx) td:where(.svelte-u14cgx) {
  margin: 0;
  padding: 0;
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-search-box-background:where(.svelte-u14cgx) {
  background: var(--jse-table-header-background, #f5f5f5);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-invisible-end-section:where(.svelte-u14cgx) td:where(.svelte-u14cgx) {
  padding-bottom: var(--jse-padding, 10px);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx):hover {
  background-color: var(--jse-table-row-odd-background, rgba(0, 0, 0, 0.05));
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell:where(.svelte-u14cgx) {
  padding: 0 var(--jse-padding, 10px) 0 0;
  vertical-align: top;
  white-space: nowrap;
  height: var(--jse-line-height, calc(1em + 4px));
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell.jse-table-cell-header:where(.svelte-u14cgx), .jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell.jse-table-cell-gutter:where(.svelte-u14cgx) {
  font-weight: normal;
  text-align: left;
  color: var(--jse-text-readonly, #8d8d8d);
  background: var(--jse-table-header-background, #f5f5f5);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell.jse-table-cell-header:where(.svelte-u14cgx) {
  padding: 0;
  position: sticky;
  top: 0;
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell.jse-table-cell-header:where(.svelte-u14cgx) .jse-table-root-error:where(.svelte-u14cgx) {
  padding: calc(0.5 * var(--jse-padding, 10px)) var(--jse-padding, 10px) calc(0.5 * var(--jse-padding, 10px)) calc(0.5 * var(--jse-padding, 10px));
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell.jse-table-cell-gutter:where(.svelte-u14cgx) {
  padding: 0 var(--jse-padding, 10px) 0 calc(0.5 * var(--jse-padding, 10px));
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell:where(.svelte-u14cgx) .jse-value-outer:where(.svelte-u14cgx) {
  display: inline-block;
  cursor: var(--jse-contents-cursor, pointer);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell:where(.svelte-u14cgx) .jse-value-outer:where(.svelte-u14cgx):hover {
  background: var(--jse-hover-background-color, rgba(0, 0, 0, 0.06));
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell:where(.svelte-u14cgx) .jse-value-outer.jse-selected-value:where(.svelte-u14cgx) {
  background: var(--jse-selection-background-color, #d3d3d3);
}
.jse-table-mode.svelte-u14cgx .jse-contents:where(.svelte-u14cgx) table.jse-table-main:where(.svelte-u14cgx) .jse-table-row:where(.svelte-u14cgx) .jse-table-cell:where(.svelte-u14cgx) .jse-context-menu-anchor:where(.svelte-u14cgx) {
  display: inline-flex;
  position: relative;
  vertical-align: top;
}
.jse-table-mode.svelte-u14cgx .jse-contents.jse-contents-loading:where(.svelte-u14cgx) {
  align-items: unset;
}
.jse-table-mode.svelte-u14cgx .jse-contents.jse-contents-loading:where(.svelte-u14cgx) .jse-loading-space:where(.svelte-u14cgx) {
  flex: 1;
}
.jse-table-mode.svelte-u14cgx .jse-contents.jse-contents-loading:where(.svelte-u14cgx) .jse-loading:where(.svelte-u14cgx) {
  flex: 2;
  text-align: center;
  color: var(--jse-panel-color-readonly, #b2b2b2);
  box-sizing: border-box;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
}`);var G1=L('<div class="jse-table-root-error svelte-u14cgx"><!></div>'),X1=L('<th class="jse-table-cell jse-table-cell-header svelte-u14cgx"><!></th>'),Z1=L('<th class="jse-table-cell jse-table-cell-header svelte-u14cgx"><!></th>'),ey=L('<th class="jse-table-cell jse-table-cell-gutter svelte-u14cgx"> <!></th>'),ny=L('<div class="jse-context-menu-anchor svelte-u14cgx"><!></div>'),ty=L('<td class="jse-table-cell svelte-u14cgx"><div><!><!></div> <!></td>'),oy=L('<td class="jse-table-cell svelte-u14cgx"></td>'),ry=L('<tr class="jse-table-row svelte-u14cgx"><!><!><!></tr>'),ay=L('<div class="jse-search-box-container svelte-u14cgx"><!></div> <div class="jse-contents svelte-u14cgx"><table class="jse-table-main svelte-u14cgx"><tbody><tr class="jse-table-row jse-table-row-header svelte-u14cgx"><th class="jse-table-cell jse-table-cell-header svelte-u14cgx"><!></th><!><!></tr><tr><td class="svelte-u14cgx"></td></tr><!><tr class="jse-table-invisible-end-section svelte-u14cgx"><td class="svelte-u14cgx"></td></tr></tbody></table></div> <!> <!> <!>',1),sy=L("<!> <!>",1),iy=L('<label class="jse-hidden-input-label svelte-u14cgx"><input type="text" tabindex="-1" class="jse-hidden-input svelte-u14cgx"></label> <!>',1),ly=L('<div class="jse-contents jse-contents-loading svelte-u14cgx"><div class="jse-loading-space svelte-u14cgx"></div> <div class="jse-loading svelte-u14cgx">loading...</div></div>'),cy=L('<div role="table"><!> <!></div> <!> <!>',1);function uy(e,n){en(n,!1);var o=E(void 0,!0),r=E(void 0,!0),s=E(void 0,!0),a=So("jsoneditor:TableMode"),{openAbsolutePopup:i,closeAbsolutePopup:l}=Ma("absolute-popup"),c=kh(),d=ys(),u=ys(),h=typeof window>"u";a("isSSR:",h);var p=f(n,"readOnly",9),m=f(n,"externalContent",9),g=f(n,"externalSelection",9),b=f(n,"history",9),j=f(n,"truncateTextSize",9),x=f(n,"mainMenuBar",9),k=f(n,"escapeControlCharacters",9),$=f(n,"escapeUnicodeCharacters",9),S=f(n,"flattenColumns",9),y=f(n,"parser",9),R=f(n,"parseMemoizeOne",9),N=f(n,"validator",9),_=f(n,"validationParser",9),z=f(n,"indentation",9),W=f(n,"onChange",9),X=f(n,"onChangeMode",9),H=f(n,"onSelect",9),ne=f(n,"onUndo",9),ye=f(n,"onRedo",9),te=f(n,"onRenderValue",9),ve=f(n,"onRenderMenu",9),Re=f(n,"onRenderContextMenu",9),Ee=f(n,"onFocus",9),fe=f(n,"onBlur",9),Se=f(n,"onSortModal",9),we=f(n,"onTransformModal",9),Ne=f(n,"onJSONEditorModal",9),de=E(void 0,!0),oe=E(void 0,!0),ce=E(void 0,!0),an=E(void 0,!0),Mn=E(void 0,!0);jd({onMount:Po,onDestroy:rr,getWindow:()=>Ni(t(oe)),hasFocus:()=>$n&&document.hasFocus()||od(t(oe)),onFocus:()=>{De=!0,Ee()&&Ee()()},onBlur:()=>{De=!1,fe()&&fe()()}});var Y,I=E(void 0,!0),ee=E(void 0,!0),P=E(void 0,!0),pe=E(void 0,!0),K=E(void 0,!0),Me=E(!1,!0),V=E(!1,!0);function T(C){v(K,(Y=C)?uh(t(I),Y.items):void 0)}function xn(C){return Qe.apply(this,arguments)}function Qe(){return(Qe=bn(function*(C){v(D,void 0),yield Wn(C)})).apply(this,arguments)}function cn(){v(Me,!1),v(V,!1),yn()}var sn=E(1e4,!0),je=E([],!0),Xe=E(void 0,!0),$n=!1,De=!1,Nn=E(!1,!0),ge=E({},!0),qe=E(600,!0),Ae=E(0,!0),Ue=18;function Fn(C){v(D,C)}function gn(C){t(D)&&C!==void 0&&(pa(C,qa(t(D)))&&pa(C,Be(t(D)))||(a("clearing selection: path does not exist anymore",t(D)),v(D,void 0)))}var Ve=E(t(I)!==void 0?gu({json:t(I)}):void 0,!0),D=E(wi(g())?g():void 0,!0),Ke=E(void 0,!0),Ge=E(!1,!0);function Ct(C){if(!p()){a("onSortByHeader",C);var G=C.sortDirection===lr.desc?-1:1;Qn(Mh(t(I),[],C.path,G),(re,ue)=>({state:ue,sortedColumn:C}))}}Po(()=>{t(D)&&Kt(Be(t(D)))});var _e=E(void 0,!0);function Rn(C){if(C.json!==void 0||C.text!==void 0){var G=t(I)!==void 0&&C.json!==void 0;b().add({type:"tree",undo:{patch:G?[{op:"replace",path:"",value:C.json}]:void 0,json:C.json,text:C.text,documentState:C.documentState,textIsRepaired:C.textIsRepaired,selection:Cr(C.selection),sortedColumn:C.sortedColumn},redo:{patch:G?[{op:"replace",path:"",value:t(I)}]:void 0,json:t(I),text:t(ee),documentState:t(Ve),textIsRepaired:t(Ge),selection:Cr(t(D)),sortedColumn:t(Ke)}})}}var Un=E([],!0),co=vi(Ch);function ut(C,G,re,ue){fs(()=>{var xe;try{xe=co(C,G,re,ue)}catch(Te){xe=[{path:[],message:"Failed to validate: "+Te.message,severity:pr.warning}]}et(xe,t(Un))||(a("validationErrors changed:",xe),v(Un,xe))},xe=>a("validationErrors updated in ".concat(xe," ms")))}function _n(){return a("validate"),t(P)?{parseError:t(P),isRepairable:!1}:(ut(t(I),N(),y(),_()),Mt(t(Un))?void 0:{validationErrors:t(Un)})}function Lt(C,G){if(a("patch",C,G),t(I)===void 0)throw new Error("Cannot apply patch: no JSON");var re=t(I),ue={json:void 0,text:t(ee),documentState:t(Ve),selection:Cr(t(D)),sortedColumn:t(Ke),textIsRepaired:t(Ge)},xe=ch(t(I),C),Te=Gp(t(I),t(Ve),C),Pe=Zx(t(Ke),C,t(je)),Ze=typeof G=="function"?G(Te.json,Te.documentState,t(D)):void 0;return v(I,(Ze==null?void 0:Ze.json)!==void 0?Ze.json:Te.json),v(Ve,(Ze==null?void 0:Ze.state)!==void 0?Ze.state:Te.documentState),v(D,(Ze==null?void 0:Ze.selection)!==void 0?Ze.selection:t(D)),v(Ke,(Ze==null?void 0:Ze.sortedColumn)!==void 0?Ze.sortedColumn:Pe),v(ee,void 0),v(Ge,!1),v(pe,void 0),v(P,void 0),b().add({type:"tree",undo:me({patch:xe},ue),redo:{patch:C,json:void 0,text:void 0,documentState:t(Ve),selection:Cr(t(D)),sortedColumn:t(Ke),textIsRepaired:t(Ge)}}),{json:t(I),previousJson:re,undo:xe,redo:C}}function Qn(C,G){a("handlePatch",C,G);var re={json:t(I),text:t(ee)},ue=Lt(C,G);return ft(re,ue),ue}function ft(C,G){if((C.json!==void 0||(C==null?void 0:C.text)!==void 0)&&W()){if(t(ee)!==void 0){var re={text:t(ee),json:void 0};W()(re,C,{contentErrors:_n(),patchResult:G})}else if(t(I)!==void 0){var ue={text:void 0,json:t(I)};W()(ue,C,{contentErrors:_n(),patchResult:G})}}}function rt(C){a("pasted json as text",C),v(pe,C)}function Ln(C){var G=parseInt(C[0],10),re=[String(G+1),...C.slice(1)];return pa(t(I),re)?Yn(re):Yn(C)}function yn(){a("focus"),t(an)&&(t(an).focus(),t(an).select())}function st(C){v(Ae,C.target.scrollTop)}function on(){t(D)||v(D,function(){if(wo(t(I))&&!Mt(t(I))&&!Mt(t(je)))return Yn(["0",...t(je)[0]])}())}function Jn(){if(t(Ge)&&t(I)!==void 0){var C={json:t(I),text:t(ee)},G={json:t(I),documentState:t(Ve),selection:t(D),sortedColumn:t(Ke),text:t(ee),textIsRepaired:t(Ge)};v(ee,void 0),v(Ge,!1),gn(t(I)),Rn(G),ft(C,void 0)}return{json:t(I),text:t(ee)}}function Wn(C){var{scrollToWhenVisible:G=!0}=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{},re=t(Me)?si:0,ue=Xv(C,t(je),ge,Ue),xe=ue-t(Ae)+re+Ue,Te=bt(C);if(a("scrollTo",{path:C,top:ue,scrollTop:t(Ae),elem:Te}),!t(ce))return Promise.resolve();var Pe=t(ce).getBoundingClientRect();if(Te&&!G){var Ze=Te.getBoundingClientRect();if(Ze.bottom>Pe.top&&Ze.top<Pe.bottom)return Promise.resolve()}var Bn=-Math.max(re+2*Ue,Pe.height/4);return new Promise(Te?Zn=>{c(Te,{container:t(ce),offset:Bn,duration:300,callback:()=>{Et(C),Zn()}})}:Zn=>{c(xe,{container:t(ce),offset:Bn,duration:300,callback:()=>{Gt(),Et(C),Zn()}})})}function Et(C){var G=bt(C);if(G&&t(ce)){var re=t(ce).getBoundingClientRect(),ue=G.getBoundingClientRect();if(ue.right>re.right){var xe=ue.right-re.right;Ko(ce,t(ce).scrollLeft+=xe)}if(ue.left<re.left){var Te=re.left-ue.left;Ko(ce,t(ce).scrollLeft-=Te)}}}function Kt(C){(function(G){if(t(ce)){var{rowIndex:re}=nr(G,t(je)),ue=Xv(G,t(je),ge,Ue),xe=ue+(ge[re]||Ue),Te=Ue,Pe=t(ce).getBoundingClientRect(),Ze=t(Ae),Bn=t(Ae)+Pe.height-Te;if(xe>Bn){var Zn=xe-Bn;Ko(ce,t(ce).scrollTop+=Zn)}if(ue<Ze){var Vn=Ze-ue;Ko(ce,t(ce).scrollTop-=Vn)}}})(C),Et(C)}function bt(C){var G,re,ue=t(je).find(Te=>oa(C.slice(1),Te)),xe=ue?C.slice(0,1).concat(ue):C;return(G=(re=t(ce))===null||re===void 0?void 0:re.querySelector('td[data-path="'.concat(vu(xe),'"]')))!==null&&G!==void 0?G:void 0}function vo(C){var G,{anchor:re,left:ue,top:xe,width:Te,height:Pe,offsetTop:Ze,offsetLeft:Bn,showTip:Zn}=C,Vn=function(J){var{json:se,documentState:Ie,selection:Oe,readOnly:Ce,onEditValue:ze,onEditRow:Fe,onToggleEnforceString:wn,onCut:tt,onCopy:Hn,onPaste:Ot,onRemove:dn,onDuplicateRow:eo,onInsertBeforeRow:Tt,onInsertAfterRow:Dt,onRemoveRow:ct}=J,Le=se!==void 0,Ye=!!Oe,vt=se!==void 0&&Oe?Je(se,Be(Oe)):void 0,Qt=Le&&(Ht(Oe)||Co(Oe)||wt(Oe)),ao=!Ce&&Le&&Oe!==void 0&&zl(Oe),yt=ao&&!po(vt),xt=!Ce&&Qt,In=Oe!==void 0&&Xr(se,Ie,Be(Oe));return[{type:"separator"},{type:"row",items:[{type:"column",items:[{type:"label",text:"Table cell:"},{type:"dropdown-button",main:{type:"button",onClick:()=>ze(),icon:ps,text:"Edit",title:"Edit the value (Double-click on the value)",disabled:!ao},width:"11em",items:[{type:"button",icon:ps,text:"Edit",title:"Edit the value (Double-click on the value)",onClick:()=>ze(),disabled:!ao},{type:"button",icon:In?Lf:Jf,text:"Enforce string",title:"Enforce keeping the value as string when it contains a numeric value",onClick:()=>wn(),disabled:!yt}]},{type:"dropdown-button",main:{type:"button",onClick:()=>tt(!0),icon:hs,text:"Cut",title:"Cut selected contents, formatted with indentation (Ctrl+X)",disabled:!xt},width:"10em",items:[{type:"button",icon:hs,text:"Cut formatted",title:"Cut selected contents, formatted with indentation (Ctrl+X)",onClick:()=>tt(!0),disabled:Ce||!Qt},{type:"button",icon:hs,text:"Cut compacted",title:"Cut selected contents, without indentation (Ctrl+Shift+X)",onClick:()=>tt(!1),disabled:Ce||!Qt}]},{type:"dropdown-button",main:{type:"button",onClick:()=>Hn(!0),icon:ha,text:"Copy",title:"Copy selected contents, formatted with indentation (Ctrl+C)",disabled:!Qt},width:"12em",items:[{type:"button",icon:ha,text:"Copy formatted",title:"Copy selected contents, formatted with indentation (Ctrl+C)",onClick:()=>Hn(!1),disabled:!Qt},{type:"button",icon:ha,text:"Copy compacted",title:"Copy selected contents, without indentation (Ctrl+Shift+C)",onClick:()=>Hn(!1),disabled:!Qt}]},{type:"button",onClick:()=>Ot(),icon:Bf,text:"Paste",title:"Paste clipboard contents (Ctrl+V)",disabled:Ce||!Ye},{type:"button",onClick:()=>dn(),icon:Zc,text:"Remove",title:"Remove selected contents (Delete)",disabled:Ce||!Qt}]},{type:"column",items:[{type:"label",text:"Table row:"},{type:"button",onClick:()=>Fe(),icon:ps,text:"Edit row",title:"Edit the current row",disabled:Ce||!Ye||!Le},{type:"button",onClick:()=>eo(),icon:Wf,text:"Duplicate row",title:"Duplicate the current row (Ctrl+D)",disabled:Ce||!Ye||!Le},{type:"button",onClick:()=>Tt(),icon:cs,text:"Insert before",title:"Insert a row before the current row",disabled:Ce||!Ye||!Le},{type:"button",onClick:()=>Dt(),icon:cs,text:"Insert after",title:"Insert a row after the current row",disabled:Ce||!Ye||!Le},{type:"button",onClick:()=>ct(),icon:Zc,text:"Remove row",title:"Remove current row",disabled:Ce||!Ye||!Le}]}]}]}({json:t(I),documentState:t(Ve),selection:t(D),readOnly:p(),onEditValue:jo,onEditRow:fo,onToggleEnforceString:Pt,onCut:He,onCopy:We,onPaste:le,onRemove:dt,onDuplicateRow:pt,onInsertBeforeRow:un,onInsertAfterRow:En,onRemoveRow:tn}),Cn=(G=Re()(Vn))!==null&&G!==void 0?G:Vn;if(Cn!==!1){var Rt={left:ue,top:xe,offsetTop:Ze,offsetLeft:Bn,width:Te,height:Pe,anchor:re,closeOnOuterClick:!0,onClose:()=>{$n=!1,yn()}};$n=!0;var w=i(Uh,{tip:Zn?"Tip: you can open this context menu via right-click or with Ctrl+Q":void 0,items:Cn,onRequestClose(){l(w),yn()}},Rt)}}function oo(C){if(!Vo(t(D)))if(C&&(C.stopPropagation(),C.preventDefault()),C&&C.type==="contextmenu"&&C.target!==t(an))vo({left:C.clientX,top:C.clientY,width:Wr,height:Br,showTip:!1});else{var G,re=(G=t(ce))===null||G===void 0?void 0:G.querySelector(".jse-table-cell.jse-selected-value");if(re)vo({anchor:re,offsetTop:2,width:Wr,height:Br,showTip:!1});else{var ue,xe=(ue=t(ce))===null||ue===void 0?void 0:ue.getBoundingClientRect();xe&&vo({top:xe.top+2,left:xe.left+2,width:Wr,height:Br,showTip:!1})}}}function uo(C){vo({anchor:Vp(C.target,"BUTTON"),offsetTop:0,width:Wr,height:Br,showTip:!0})}function jo(){if(!p()&&t(D)){var C=Be(t(D));po(Je(t(I),C))?U(C):v(D,Yn(C))}}function fo(){!p()&&t(D)&&U(Be(t(D)).slice(0,1))}function Pt(){if(!p()&&wt(t(D))){var C=t(D).path,G=Sn(C),re=Je(t(I),C),ue=!Xr(t(I),t(Ve),C),xe=ue?String(re):_s(String(re),y());a("handleToggleEnforceString",{enforceString:ue,value:re,updatedValue:xe}),Qn([{op:"replace",path:G,value:xe}],(Te,Pe)=>({state:nc(t(I),Pe,C,{type:"value",enforceString:ue})}))}}function It(){return Zt.apply(this,arguments)}function Zt(){return(Zt=bn(function*(){if(a("apply pasted json",t(pe)),t(pe)){var{onPasteAsJson:C}=t(pe);C(),setTimeout(yn)}})).apply(this,arguments)}function le(){return Gn.apply(this,arguments)}function Gn(){return(Gn=bn(function*(){try{At(yield navigator.clipboard.readText())}catch(C){console.error(C),v(Nn,!0)}})).apply(this,arguments)}function B(){a("clear pasted json"),v(pe,void 0),yn()}function ke(){X()(yo.text)}function He(C){return zn.apply(this,arguments)}function zn(){return(zn=bn(function*(C){yield Rh({json:t(I),selection:t(D),indentation:C?z():void 0,readOnly:p(),parser:y(),onPatch:Qn})})).apply(this,arguments)}function We(){return Tn.apply(this,arguments)}function Tn(){return Tn=bn(function*(){var C=!(arguments.length>0&&arguments[0]!==void 0)||arguments[0];t(I)!==void 0&&(yield Th({json:t(I),selection:t(D),indentation:C?z():void 0,parser:y()}))}),Tn.apply(this,arguments)}function dt(){Ih({json:t(I),text:t(ee),selection:t(D),keepSelection:!0,readOnly:p(),onChange:W(),onPatch:Qn})}function On(C){p()||(a("extract",{path:C}),Qn(sh(t(I),Yn(C))))}function pt(){(function(C){var{json:G,selection:re,columns:ue,readOnly:xe,onPatch:Te}=C;if(!xe&&G!==void 0&&re&&ds(re)){var{rowIndex:Pe,columnIndex:Ze}=nr(Be(re),ue);No("duplicate row",{rowIndex:Pe});var Bn=[String(Pe)];Te(ah(G,[Bn]),(Zn,Vn)=>({state:Vn,selection:Yn(Aa({rowIndex:Pe<G.length?Pe+1:Pe,columnIndex:Ze},ue))}))}})({json:t(I),selection:t(D),columns:t(je),readOnly:p(),onPatch:Qn})}function un(){(function(C){var{json:G,selection:re,columns:ue,readOnly:xe,onPatch:Te}=C;if(!xe&&G!==void 0&&re&&ds(re)){var{rowIndex:Pe}=nr(Be(re),ue);No("insert before row",{rowIndex:Pe}),Te(vs(G,[String(Pe)],[{key:"",value:Oo(G[0])?{}:""}]))}})({json:t(I),selection:t(D),columns:t(je),readOnly:p(),onPatch:Qn})}function En(){(function(C){var{json:G,selection:re,columns:ue,readOnly:xe,onPatch:Te}=C;if(!xe&&G!==void 0&&re&&ds(re)){var{rowIndex:Pe,columnIndex:Ze}=nr(Be(re),ue);No("insert after row",{rowIndex:Pe});var Bn=Pe+1,Zn=[String(Bn)],Vn=[{key:"",value:Oo(G[0])?{}:""}];Te(Bn<G.length?vs(G,Zn,Vn):bu(G,[],Vn),(Cn,Rt)=>({state:Rt,selection:Yn(Aa({rowIndex:Bn,columnIndex:Ze},ue))}))}})({json:t(I),selection:t(D),columns:t(je),readOnly:p(),onPatch:Qn})}function tn(){(function(C){var{json:G,selection:re,columns:ue,readOnly:xe,onPatch:Te}=C;if(!xe&&G!==void 0&&re&&ds(re)){var{rowIndex:Pe,columnIndex:Ze}=nr(Be(re),ue);No("remove row",{rowIndex:Pe}),Te(Ml([[String(Pe)]]),(Bn,Zn)=>{var Vn=Pe<Bn.length?Pe:Pe>0?Pe-1:void 0,Cn=Vn!==void 0?Yn(Aa({rowIndex:Vn,columnIndex:Ze},ue)):void 0;return No("remove row new selection",{rowIndex:Pe,newRowIndex:Vn,newSelection:Cn}),{state:Zn,selection:Cn}})}})({json:t(I),selection:t(D),columns:t(je),readOnly:p(),onPatch:Qn})}function pn(){return(pn=bn(function*(C){yield qh({char:C,selectInside:!1,json:t(I),selection:t(D),readOnly:p(),parser:y(),onPatch:Qn,onReplaceJson:Dn,onSelect:Fn})})).apply(this,arguments)}function St(C){var G;C.preventDefault(),At((G=C.clipboardData)===null||G===void 0?void 0:G.getData("text/plain"))}function At(C){C!==void 0&&Ph({clipboardText:C,json:t(I),selection:t(D),readOnly:p(),parser:y(),onPatch:Qn,onChangeText:Pn,openRepairModal:Z})}function Dn(C,G){var re={json:t(I),text:t(ee)},ue={json:t(I),documentState:t(Ve),selection:t(D),sortedColumn:t(Ke),text:t(ee),textIsRepaired:t(Ge)},xe=Yo(C,t(Ve)),Te=typeof G=="function"?G(C,xe,t(D)):void 0;v(I,(Te==null?void 0:Te.json)!==void 0?Te.json:C),v(Ve,(Te==null?void 0:Te.state)!==void 0?Te.state:xe),v(D,(Te==null?void 0:Te.selection)!==void 0?Te.selection:t(D)),v(Ke,void 0),v(ee,void 0),v(Ge,!1),v(P,void 0),gn(t(I)),Rn(ue),ft(re,void 0)}function Pn(C,G){a("handleChangeText");var re={json:t(I),text:t(ee)},ue={json:t(I),documentState:t(Ve),selection:t(D),sortedColumn:t(Ke),text:t(ee),textIsRepaired:t(Ge)};try{v(I,R()(C)),v(Ve,Yo(t(I),t(Ve))),v(ee,void 0),v(Ge,!1),v(P,void 0)}catch(Te){try{v(I,R()(Or(C))),v(Ve,Yo(t(I),t(Ve))),v(ee,C),v(Ge,!0),v(P,void 0)}catch{v(I,void 0),v(Ve,void 0),v(ee,C),v(Ge,!1),v(P,t(ee)!==""?Ss(t(ee),Te.message||String(Te)):void 0)}}if(typeof G=="function"){var xe=G(t(I),t(Ve),t(D));v(I,(xe==null?void 0:xe.json)!==void 0?xe.json:t(I)),v(Ve,(xe==null?void 0:xe.state)!==void 0?xe.state:t(Ve)),v(D,(xe==null?void 0:xe.selection)!==void 0?xe.selection:t(D))}gn(t(I)),Rn(ue),ft(re,void 0)}function qn(C){a("select validation error",C),v(D,Yn(C.path)),Wn(C.path)}function Jt(C){if(t(I)!==void 0){var{id:G,onTransform:re,onClose:ue}=C,xe=C.rootPath||[];$n=!0,we()({id:G||u,json:t(I),rootPath:xe||[],onTransform:Te=>{re?re({operations:Te,json:t(I),transformedJson:zr(t(I),Te)}):(a("onTransform",xe,Te),Qn(Te))},onClose:()=>{$n=!1,setTimeout(yn),ue&&ue()}})}}function U(C){a("openJSONEditorModal",{path:C}),$n=!0,Ne()({content:{json:Je(t(I),C)},path:C,onPatch:Qn,onClose:()=>{$n=!1,setTimeout(yn)}})}function Z(C,G){v(Mn,{text:C,onParse:re=>Zl(re,ue=>qi(ue,y())),onRepair:Up,onApply:G,onClose:yn})}function he(){(function(C){p()||t(I)===void 0||($n=!0,Se()({id:d,json:t(I),rootPath:C,onSort:G=>{var{operations:re,itemPath:ue,direction:xe}=G;a("onSort",re,C,ue,xe),Qn(re,(Te,Pe)=>({state:Pe,sortedColumn:{path:ue,sortDirection:xe===-1?lr.desc:lr.asc}}))},onClose:()=>{$n=!1,setTimeout(yn)}}))})([])}function An(){Jt({rootPath:[]})}function it(C){a("openFind",{findAndReplace:C}),v(Me,!1),v(V,!1),Gt(),v(Me,!0),v(V,C)}function Xn(){if(!p()&&b().canUndo){var C=b().undo();if(Sl(C)){var G={json:t(I),text:t(ee)};v(I,C.undo.patch?zr(t(I),C.undo.patch):C.undo.json),v(Ve,C.undo.documentState),v(D,C.undo.selection),v(Ke,C.undo.sortedColumn),v(ee,C.undo.text),v(Ge,C.undo.textIsRepaired),v(P,void 0),a("undo",{item:C,json:t(I)}),ft(G,C.undo.patch&&C.redo.patch?{json:t(I),previousJson:G.json,redo:C.undo.patch,undo:C.redo.patch}:void 0),yn(),t(D)&&Wn(Be(t(D)),{scrollToWhenVisible:!1})}else ne()(C)}}function jt(){if(!p()&&b().canRedo){var C=b().redo();if(Sl(C)){var G={json:t(I),text:t(ee)};v(I,C.redo.patch?zr(t(I),C.redo.patch):C.redo.json),v(Ve,C.redo.documentState),v(D,C.redo.selection),v(Ke,C.redo.sortedColumn),v(ee,C.redo.text),v(Ge,C.redo.textIsRepaired),v(P,void 0),a("redo",{item:C,json:t(I)}),ft(G,C.undo.patch&&C.redo.patch?{json:t(I),previousJson:G.json,redo:C.redo.patch,undo:C.undo.patch}:void 0),yn(),t(D)&&Wn(Be(t(D)),{scrollToWhenVisible:!1})}else ye()(C)}}function Ut(C){v(qe,C.getBoundingClientRect().height)}F(()=>(O(k()),O($())),()=>{v(de,nd({escapeControlCharacters:k(),escapeUnicodeCharacters:$()}))}),F(()=>t(Me),()=>{(function(C){if(t(ce)){var G=C?si:-100;t(ce).scrollTo({top:Ko(ce,t(ce).scrollTop+=G),left:t(ce).scrollLeft})}})(t(Me))}),F(()=>O(m()),()=>{(function(C){var G={json:t(I)},re=gi(C)?C.text!==t(ee):!et(G.json,C.json);if(a("update external content",{isChanged:re}),re){var ue={json:t(I),documentState:t(Ve),selection:t(D),sortedColumn:t(Ke),text:t(ee),textIsRepaired:t(Ge)};if(gi(C))try{v(I,R()(C.text)),v(Ve,Yo(t(I),t(Ve))),v(ee,C.text),v(Ge,!1),v(P,void 0)}catch(xe){try{v(I,R()(Or(C.text))),v(Ve,Yo(t(I),t(Ve))),v(ee,C.text),v(Ge,!0),v(P,void 0)}catch{v(I,void 0),v(Ve,void 0),v(ee,C.text),v(Ge,!1),v(P,t(ee)!==""?Ss(t(ee),xe.message||String(xe)):void 0)}}else v(I,C.json),v(Ve,Yo(t(I),t(Ve))),v(ee,void 0),v(Ge,!1),v(P,void 0);gn(t(I)),v(Ke,void 0),Rn(ue)}})(m())}),F(()=>O(g()),()=>{(function(C){et(t(D),C)||(a("applyExternalSelection",{selection:t(D),externalSelection:C}),wi(C)&&v(D,C))})(g())}),F(()=>(t(je),t(I),O(S()),t(sn)),()=>{v(je,wo(t(I))?function(C,G){var re=new Set(G.map(Sn)),ue=new Set(C.map(Sn));for(var xe of re)ue.has(xe)||re.delete(xe);for(var Te of ue)re.has(Te)||re.add(Te);return[...re].map(Sr)}(Kx(t(I),S(),t(sn)),t(je)):[])}),F(()=>(t(I),t(je)),()=>{v(Xe,!(!t(I)||Mt(t(je))))}),F(()=>(t(I),t(sn)),()=>{v(o,Array.isArray(t(I))&&t(I).length>t(sn))}),F(()=>(t(Ae),t(qe),t(I),t(Me),si),()=>{v(r,Qx(t(Ae),t(qe),t(I),ge,Ue,t(Me)?si:0))}),F(()=>t(I),()=>{t(I),t(ce)&&t(ce).scrollTo({top:t(ce).scrollTop,left:t(ce).scrollLeft})}),F(()=>t(D),()=>{var C;C=t(D),et(C,g())||(a("onSelect",C),H()(C))}),F(()=>(O(p()),O(j()),O(y()),t(de),t(I),t(Ve),O(te())),()=>{v(_e,{mode:yo.table,readOnly:p(),truncateTextSize:j(),parser:y(),normalization:t(de),getJson:()=>t(I),getDocumentState:()=>t(Ve),findElement:bt,findNextInside:Ln,focus:yn,onPatch:(C,G)=>Qn(function(re,ue){return re.flatMap(xe=>{if(Ju(xe)){var Te=Sr(xe.path);if(Te.length>0){for(var Pe=[xe],Ze=ot(Te);Ze.length>0&&!pa(ue,Ze);)Pe.unshift({op:"add",path:Sn(Ze),value:{}}),Ze=ot(Ze);return Pe}}return xe})}(C,t(I)),G),onSelect:Fn,onFind:it,onPasteJson:rt,onRenderValue:te()})}),F(()=>(t(I),O(N()),O(y()),O(_())),()=>{ut(t(I),N(),y(),_())}),F(()=>(t(Un),t(je)),()=>{v(s,Gx(t(Un),t(je)))}),mt(),jn(!0);var qt=cy();ie("mousedown",Qr,function(C){!Ds(C.target,G=>G===t(oe))&&Vo(t(D))&&(a("click outside the editor, exit edit mode"),v(D,Cr(t(D))),De&&t(an)&&(t(an).focus(),t(an).blur()),a("blur (outside editor)"),t(an)&&t(an).blur())});var Bt,mn=rn(qt),_t=M(mn),vn=C=>{(function(G,re){en(re,!1);var ue=f(re,"containsValidArray",9),xe=f(re,"readOnly",9),Te=f(re,"showSearch",13,!1),Pe=f(re,"history",9),Ze=f(re,"onSort",9),Bn=f(re,"onTransform",9),Zn=f(re,"onContextMenu",9),Vn=f(re,"onUndo",9),Cn=f(re,"onRedo",9),Rt=f(re,"onRenderMenu",9);function w(){Te(!Te())}var J=E(void 0,!0),se=E(void 0,!0);F(()=>(O(xe()),O(Ze()),O(ue()),O(Bn()),O(Zn()),O(Vn()),O(Pe()),O(Cn())),()=>{v(J,xe()?[{type:"space"}]:[{type:"button",icon:pl,title:"Sort",className:"jse-sort",onClick:Ze(),disabled:xe()||!ue()},{type:"button",icon:hl,title:"Transform contents (filter, sort, project)",className:"jse-transform",onClick:Bn(),disabled:xe()||!ue()},{type:"button",icon:Jl,title:"Search (Ctrl+F)",className:"jse-search",onClick:w,disabled:!ue()},{type:"button",icon:Vf,title:sd,className:"jse-contextmenu",onClick:Zn()},{type:"separator"},{type:"button",icon:Fu,title:"Undo (Ctrl+Z)",className:"jse-undo",onClick:Vn(),disabled:!Pe().canUndo},{type:"button",icon:Lu,title:"Redo (Ctrl+Shift+Z)",className:"jse-redo",onClick:Cn(),disabled:!Pe().canRedo},{type:"space"}])}),F(()=>(O(Rt()),t(J)),()=>{v(se,Rt()(t(J))||t(J))}),mt(),jn(!0),sc(G,{get items(){return t(se)}}),nn()})(C,{get containsValidArray(){return t(Xe)},get readOnly(){return p()},get history(){return b()},onSort:he,onTransform:An,onUndo:Xn,onRedo:jt,onContextMenu:uo,get onRenderMenu(){return ve()},get showSearch(){return t(Me)},set showSearch(G){v(Me,G)},$$legacy:!0})};Q(_t,C=>{x()&&C(vn)});var xo=q(_t,2),$e=C=>{var G=iy(),re=rn(G),ue=M(re);ue.readOnly=!0,Yt(ue,Ze=>v(an,Ze),()=>t(an));var xe=q(re,2),Te=Ze=>{var Bn=ay(),Zn=rn(Bn);$h(M(Zn),{get json(){return t(I)},get documentState(){return t(Ve)},get parser(){return y()},get showSearch(){return t(Me)},get showReplace(){return t(V)},get readOnly(){return p()},get columns(){return t(je)},onSearch:T,onFocus:xn,onPatch:Qn,onClose:cn});var Vn=q(Zn,2),Cn=M(Vn),Rt=M(Cn),w=M(Rt),J=M(w),se=M(J),Ie=Ye=>{var vt=to(),Qt=ae(()=>{var xt;return Wc([],(xt=t(s))===null||xt===void 0?void 0:xt.root)}),ao=rn(vt),yt=xt=>{var In=G1();xs(M(In),{get validationError(){return t(Qt)},onExpand:tr}),A(xt,In)};Q(ao,xt=>{t(Qt)&&xt(yt)}),A(Ye,vt)};Q(se,Ye=>{var vt;Mt((vt=t(s))===null||vt===void 0?void 0:vt.root)||Ye(Ie)});var Oe=q(J);lo(Oe,1,()=>t(je),ho,(Ye,vt)=>{var Qt=X1();(function(ao,yt){en(yt,!1);var xt=E(void 0,!0),In=E(void 0,!0),go=E(void 0,!0),Ao=f(yt,"path",9),sr=f(yt,"sortedColumn",9),wr=f(yt,"readOnly",9),ir=f(yt,"onSort",9);F(()=>(O(Ao()),vr),()=>{v(xt,Mt(Ao())?"values":vr(Ao()))}),F(()=>(O(sr()),O(Ao())),()=>{var Wt;v(In,sr()&&et(Ao(),(Wt=sr())===null||Wt===void 0?void 0:Wt.path)?sr().sortDirection:void 0)}),F(()=>(t(In),yv),()=>{v(go,t(In)?yv[t(In)]:void 0)}),mt(),jn(!0);var Mo,Do=D1(),_r=M(Do),Xa=M(_r),Zo=q(_r,2),mo=Wt=>{var ko=_1(),Dr=M(ko),Jo=ae(()=>t(In)===lr.asc?Ca:Om);nt(Dr,{get data(){return t(Jo)}}),be(()=>ht(ko,"title","Currently sorted in ".concat(t(go)," order"))),A(Wt,ko)};Q(Zo,Wt=>{t(In)!==void 0&&Wt(mo)}),be((Wt,ko)=>{Mo=kn(Do,1,"jse-column-header svelte-2i3vdx",null,Mo,Wt),ht(Do,"title",wr()?t(xt):t(xt)+" (Click to sort the data by this column)"),ln(Xa,ko)},[()=>({"jse-readonly":wr()}),()=>bi(t(xt),50)],ae),ie("click",Do,function(){wr()||ir()({path:Ao(),sortDirection:t(In)===lr.asc?lr.desc:lr.asc})}),A(ao,Do),nn()})(M(Qt),{get path(){return t(vt)},get sortedColumn(){return t(Ke)},get readOnly(){return p()},onSort:Ct}),A(Ye,Qt)});var Ce=q(Oe),ze=Ye=>{var vt=Z1(),Qt=M(vt),ao=ae(()=>Array.isArray(t(I))?t(I).length:0);(function(yt,xt){en(xt,!1);var In=f(xt,"count",9),go=f(xt,"maxSampleCount",9),Ao=f(xt,"readOnly",9),sr=f(xt,"onRefresh",9);jn(!0);var wr,ir=Q1();nt(M(ir),{data:Mm}),be(Mo=>{wr=kn(ir,1,"jse-column-header svelte-fzj761",null,wr,Mo),ht(ir,"title","The Columns are created by sampling ".concat(go()," items out of ").concat(In(),". ")+"If you're missing a column, click here to sample all of the items instead of a subset. This is slower.")},[()=>({"jse-readonly":Ao()})],ae),ie("click",ir,()=>sr()()),A(yt,ir),nn()})(Qt,{get count(){return t(ao)},get maxSampleCount(){return t(sn)},get readOnly(){return p()},onRefresh:()=>v(sn,1/0)}),A(Ye,vt)};Q(Ce,Ye=>{t(o)&&Ye(ze)});var Fe,wn,tt=q(w),Hn=M(tt),Ot=q(tt);lo(Ot,1,()=>t(r).visibleItems,ho,(Ye,vt,Qt)=>{var ao=ry(),yt=ae(()=>t(r).startIndex+Qt),xt=ae(()=>t(s).rows[t(yt)]),In=ae(()=>{var Mo;return Wc([String(t(yt))],(Mo=t(xt))===null||Mo===void 0?void 0:Mo.row)}),go=ae(()=>Ia(t(I),t(K),[String(t(yt))])),Ao=M(ao);zp(Ao,()=>t(yt),Mo=>{var Do=ey(),_r=M(Do),Xa=q(_r),Zo=mo=>{xs(mo,{get validationError(){return t(In)},onExpand:tr})};Q(Xa,mo=>{t(In)&&mo(Zo)}),Uo(Do,(mo,Wt)=>tl==null?void 0:tl(mo,Wt),()=>mo=>function(Wt,ko){ge[ko]=Wt.getBoundingClientRect().height}(mo,t(yt))),be(()=>{var mo;return ln(_r,"".concat((mo=t(yt))!==null&&mo!==void 0?mo:""," "))}),A(Mo,Do)});var sr=q(Ao);lo(sr,1,()=>t(je),ho,(Mo,Do,_r,Xa)=>{var Zo,mo=ty(),Wt=ae(()=>[String(t(yt))].concat(t(Do))),ko=ae(()=>Je(t(vt),t(Do))),Dr=ae(()=>wt(t(D))&&oa(t(D).path,t(Wt))),Jo=ae(()=>{var Ft;return(Ft=t(xt))===null||Ft===void 0?void 0:Ft.columns[_r]}),Ls=ae(()=>Wc(t(Wt),t(Jo))),Js=M(mo),Za=M(Js),vc=Ft=>{var Io=ae(()=>xu(Ia(t(vt),t(go),t(Do)))),Vs=ae(()=>!!t(Io)&&t(Io).some(Ea=>Ea.active)),Hs=ae(()=>!Mt(t(Io)));(function(Ea,Bo){en(Bo,!1);var gc=f(Bo,"path",9),es=f(Bo,"value",9),Ys=f(Bo,"parser",9),mg=f(Bo,"isSelected",9),bg=f(Bo,"containsSearchResult",9),jg=f(Bo,"containsActiveSearchResult",9),xg=f(Bo,"onEdit",9);jn(!0);var Nd,Li=U1(),yg=M(Li);be((Ks,wg)=>{Nd=kn(Li,1,"jse-inline-value svelte-h57m0p",null,Nd,Ks),ln(yg,wg)},[()=>({"jse-selected":mg(),"jse-highlight":bg(),"jse-active":jg()}),()=>{var Ks;return bi((Ks=Ys().stringify(es()))!==null&&Ks!==void 0?Ks:"",50)}],ae),ie("dblclick",Li,()=>xg()(gc())),A(Ea,Li),nn()})(Ft,{get path(){return t(Wt)},get value(){return t(ko)},get parser(){return y()},get isSelected(){return t(Dr)},get containsSearchResult(){return t(Hs)},get containsActiveSearchResult(){return t(Vs)},onEdit:U})},Bs=Ft=>{var Io=ae(()=>{var Bo;return(Bo=Ia(t(I),t(K),t(Wt)))===null||Bo===void 0?void 0:Bo.searchResults}),Vs=ae(()=>t(ko)!==void 0?t(ko):""),Hs=ae(()=>Xr(t(I),t(Ve),t(Wt))),Ea=ae(()=>t(Dr)?t(D):void 0);Oh(Ft,{get path(){return t(Wt)},get value(){return t(Vs)},get enforceString(){return t(Hs)},get selection(){return t(Ea)},get searchResultItems(){return t(Io)},get context(){return t(_e)}})};Q(Za,Ft=>{po(t(ko))?Ft(vc):Ft(Bs,!1)});var Ws=q(Za),fc=Ft=>{var Io=ny();da(M(Io),{selected:!0,onContextMenu:vo}),A(Ft,Io)};Q(Ws,Ft=>{p()||!t(Dr)||Vo(t(D))||Ft(fc)});var pc=q(Js,2),hc=Ft=>{xs(Ft,{get validationError(){return t(Ls)},onExpand:tr})};Q(pc,Ft=>{t(Ls)&&Ft(hc)}),be((Ft,Io)=>{ht(mo,"data-path",Ft),Zo=kn(Js,1,"jse-value-outer svelte-u14cgx",null,Zo,Io)},[()=>vu(t(Wt)),()=>({"jse-selected-value":t(Dr)})],ae),A(Mo,mo)});var wr=q(sr),ir=Mo=>{A(Mo,oy())};Q(wr,Mo=>{t(o)&&Mo(ir)}),A(Ye,ao)});var dn,eo=M(q(Ot));Yt(Vn,Ye=>v(ce,Ye),()=>t(ce)),Uo(Vn,(Ye,vt)=>tl==null?void 0:tl(Ye,vt),()=>Ut),$o(()=>ie("scroll",Vn,st));var Tt=q(Vn,2),Dt=Ye=>{var vt=ae(()=>"You pasted a JSON ".concat(Array.isArray(t(pe).contents)?"array":"object"," as text"));jr(Ye,{type:"info",get message(){return t(vt)},actions:[{icon:ws,text:"Paste as JSON instead",title:"Paste the text as JSON instead of a single value",onMouseDown:It},{text:"Leave as is",title:"Keep the pasted content as a single value",onClick:B}]})};Q(Tt,Ye=>{t(pe)&&Ye(Dt)});var ct=q(Tt,2),Le=Ye=>{var vt=ae(()=>p()?[]:[{icon:Bu,text:"Ok",title:"Accept the repaired document",onClick:Jn},{icon:gl,text:"Repair manually instead",title:"Leave the document unchanged and repair it manually instead",onClick:ke}]);jr(Ye,{type:"success",message:"The loaded JSON document was invalid but is successfully repaired.",get actions(){return t(vt)},onClose:yn})};Q(ct,Ye=>{t(Ge)&&Ye(Le)}),xd(q(ct,2),{get validationErrors(){return t(Un)},selectError:qn}),be(Ye=>{Fe=kn(tt,1,"jse-table-invisible-start-section svelte-u14cgx",null,Fe,Ye),ht(Hn,"colspan",t(je).length),wn=fr(Hn,"",wn,{height:t(r).startHeight+"px"}),ht(eo,"colspan",t(je).length),dn=fr(eo,"",dn,{height:t(r).endHeight+"px"})},[()=>({"jse-search-box-background":t(Me)})],ae),A(Ze,Bn)},Pe=(Ze,Bn)=>{var Zn=Cn=>{var Rt=sy(),w=rn(Rt),J=ae(()=>p()?[]:[{icon:gl,text:"Repair manually",title:'Open the document in "code" mode and repair it manually',onClick:ke}]);jr(w,{type:"error",message:"The loaded JSON document is invalid and could not be repaired automatically.",get actions(){return t(J)}}),Nh(q(w,2),{get text(){return t(ee)},get json(){return t(I)},get indentation(){return z()},get parser(){return y()}}),A(Cn,Rt)},Vn=Cn=>{K1(Cn,{get text(){return t(ee)},get json(){return t(I)},get readOnly(){return p()},get parser(){return y()},openJSONEditorModal:U,extractPath:On,get onChangeMode(){return X()},onClick:()=>{yn()}})};Q(Ze,Cn=>{t(P)&&t(ee)!==void 0&&t(ee)!==""?Cn(Zn):Cn(Vn,!1)},Bn)};Q(xe,Ze=>{t(Xe)?Ze(Te):Ze(Pe,!1)}),ie("paste",ue,St),A(C,G)},at=C=>{A(C,ly())};Q(xo,C=>{h?C(at,!1):C($e)}),Yt(mn,C=>v(oe,C),()=>t(oe));var zt=q(mn,2),lt=C=>{Sh(C,{onClose:()=>v(Nn,!1)})};Q(zt,C=>{t(Nn)&&C(lt)});var ro=q(zt,2),_o=C=>{zh(C,ya(()=>t(Mn),{onClose:()=>{var G;(G=t(Mn))===null||G===void 0||G.onClose(),v(Mn,void 0)}}))};return Q(ro,C=>{t(Mn)&&C(_o)}),be(C=>Bt=kn(mn,1,"jse-table-mode svelte-u14cgx",null,Bt,C),[()=>({"no-main-menu":!x()})],ae),ie("mousedown",mn,function(C){if(C.buttons===1||C.buttons===2){var G=C.target;G.isContentEditable||yn();var re=Hp(G);if(re){if(Vo(t(D))&&ki(t(I),t(D),re))return;v(D,Yn(re)),C.preventDefault()}}}),ie("keydown",mn,function(C){var G=ta(C);if(a("keydown",{combo:G,key:C.key}),G==="Ctrl+X"&&(C.preventDefault(),He(!0)),G==="Ctrl+Shift+X"&&(C.preventDefault(),He(!1)),G==="Ctrl+C"&&(C.preventDefault(),We(!0)),G==="Ctrl+Shift+C"&&(C.preventDefault(),We(!1)),G==="Ctrl+D"&&(C.preventDefault(),pt()),G!=="Delete"&&G!=="Backspace"||(C.preventDefault(),dt()),G==="Insert"&&C.preventDefault(),G==="Ctrl+A"&&C.preventDefault(),G==="Ctrl+Q"&&oo(C),G==="ArrowLeft"&&(C.preventDefault(),on(),t(D))){var re=function(Bn,Zn){var{rowIndex:Vn,columnIndex:Cn}=nr(Be(Zn),Bn);return Cn>0?Yn(Aa({rowIndex:Vn,columnIndex:Cn-1},Bn)):Zn}(t(je),t(D));v(D,re),Kt(Be(re))}if(G==="ArrowRight"&&(C.preventDefault(),on(),t(D))){var ue=function(Bn,Zn){var{rowIndex:Vn,columnIndex:Cn}=nr(Be(Zn),Bn);return Cn<Bn.length-1?Yn(Aa({rowIndex:Vn,columnIndex:Cn+1},Bn)):Zn}(t(je),t(D));v(D,ue),Kt(Be(ue))}if(G==="ArrowUp"&&(C.preventDefault(),on(),t(D))){var xe=function(Bn,Zn){var{rowIndex:Vn,columnIndex:Cn}=nr(Be(Zn),Bn);return Vn>0?Yn(Aa({rowIndex:Vn-1,columnIndex:Cn},Bn)):Zn}(t(je),t(D));v(D,xe),Kt(Be(xe))}if(G==="ArrowDown"&&(C.preventDefault(),on(),t(D))){var Te=function(Bn,Zn,Vn){var{rowIndex:Cn,columnIndex:Rt}=nr(Be(Vn),Zn);return Cn<Bn.length-1?Yn(Aa({rowIndex:Cn+1,columnIndex:Rt},Zn)):Vn}(t(I),t(je),t(D));v(D,Te),Kt(Be(Te))}if(G==="Enter"&&t(D)&&wt(t(D))){C.preventDefault();var Pe=t(D).path;po(Je(t(I),Pe))?U(Pe):p()||v(D,me(me({},t(D)),{},{edit:!0}))}if(G.replace(/^Shift\+/,"").length===1&&t(D))return C.preventDefault(),void function(Bn){pn.apply(this,arguments)}(C.key);if(G==="Ctrl+Enter"&&wt(t(D))){C.preventDefault();var Ze=Je(t(I),t(D).path);Xl(Ze)&&window.open(String(Ze),"_blank")}G==="Escape"&&t(D)&&(C.preventDefault(),v(D,void 0)),G==="Ctrl+F"&&(C.preventDefault(),it(!1)),G==="Ctrl+H"&&(C.preventDefault(),it(!0)),G==="Ctrl+Z"&&(C.preventDefault(),Xn()),G==="Ctrl+Shift+Z"&&(C.preventDefault(),jt())}),ie("contextmenu",mn,oo),A(e,qt),hn(n,"validate",_n),hn(n,"patch",Lt),hn(n,"focus",yn),hn(n,"acceptAutoRepair",Jn),hn(n,"scrollTo",Wn),hn(n,"findElement",bt),hn(n,"openTransformModal",Jt),nn({validate:_n,patch:Lt,focus:yn,acceptAutoRepair:Jn,scrollTo:Wn,findElement:bt,openTransformModal:Jt})}function Zv(e,n){en(n,!1);var o=f(n,"content",8),r=f(n,"selection",12),s=f(n,"readOnly",8),a=f(n,"indentation",8),i=f(n,"tabSize",8),l=f(n,"truncateTextSize",8),c=f(n,"externalMode",8),d=f(n,"mainMenuBar",8),u=f(n,"navigationBar",8),h=f(n,"statusBar",8),p=f(n,"askToFormat",8),m=f(n,"escapeControlCharacters",8),g=f(n,"escapeUnicodeCharacters",8),b=f(n,"flattenColumns",8),j=f(n,"parser",8),x=f(n,"parseMemoizeOne",8),k=f(n,"validator",8),$=f(n,"validationParser",8),S=f(n,"pathParser",8),y=f(n,"insideModal",8),R=f(n,"onChange",8),N=f(n,"onChangeMode",8),_=f(n,"onSelect",8),z=f(n,"onRenderValue",8),W=f(n,"onClassName",8),X=f(n,"onRenderMenu",8),H=f(n,"onRenderContextMenu",8),ne=f(n,"onError",8),ye=f(n,"onFocus",8),te=f(n,"onBlur",8),ve=f(n,"onSortModal",8),Re=f(n,"onTransformModal",8),Ee=f(n,"onJSONEditorModal",8),fe=E(),Se=E(),we=E(),Ne=So("jsoneditor:JSONEditorRoot"),de=E(Dh({onChange:ge=>v(de,ge)}).get()),oe=E(c());function ce(ge){if(Sv(ge)){v(oe,ge.undo.mode);var qe=t(de).items(),Ae=qe.findIndex(Fn=>Fn===ge),Ue=Ae!==-1?qe[Ae-1]:void 0;Ne("handleUndo",{index:Ae,item:ge,items:qe,prevItem:Ue}),Ue&&r(Ue.redo.selection),N()(t(oe))}}function an(ge){if(Sv(ge)){v(oe,ge.redo.mode);var qe=t(de).items(),Ae=qe.findIndex(Fn=>Fn===ge),Ue=Ae!==-1?qe[Ae+1]:void 0;Ne("handleRedo",{index:Ae,item:ge,items:qe,nextItem:Ue}),Ue&&r(Ue.undo.selection),N()(t(oe))}}var Mn=E(),Y={type:"separator"},I=E(),ee=E();function P(ge){if(t(fe))return t(fe).patch(ge);if(t(Se))return t(Se).patch(ge);if(t(we))return t(we).patch(ge);throw new Error('Method patch is not available in mode "'.concat(t(oe),'"'))}function pe(ge,qe){if(t(fe))return t(fe).expand(ge,qe);throw new Error('Method expand is not available in mode "'.concat(t(oe),'"'))}function K(ge,qe){if(t(fe))return t(fe).collapse(ge,qe);throw new Error('Method collapse is not available in mode "'.concat(t(oe),'"'))}function Me(ge){if(t(we))t(we).openTransformModal(ge);else if(t(fe))t(fe).openTransformModal(ge);else{if(!t(Se))throw new Error('Method transform is not available in mode "'.concat(t(oe),'"'));t(Se).openTransformModal(ge)}}function V(){if(t(we))return t(we).validate();if(t(fe))return t(fe).validate();if(t(Se))return t(Se).validate();throw new Error('Method validate is not available in mode "'.concat(t(oe),'"'))}function T(){return t(fe)?t(fe).acceptAutoRepair():o()}function xn(ge){if(t(fe))return t(fe).scrollTo(ge);if(t(Se))return t(Se).scrollTo(ge);throw new Error('Method scrollTo is not available in mode "'.concat(t(oe),'"'))}function Qe(ge){if(t(fe))return t(fe).findElement(ge);if(t(Se))return t(Se).findElement(ge);throw new Error('Method findElement is not available in mode "'.concat(t(oe),'"'))}function cn(){t(we)?t(we).focus():t(fe)?t(fe).focus():t(Se)&&t(Se).focus()}function sn(){return je.apply(this,arguments)}function je(){return(je=bn(function*(){t(we)&&(yield t(we).refresh())})).apply(this,arguments)}F(()=>O(c()),()=>{(function(ge){if(ge!==t(oe)){var qe={type:"mode",undo:{mode:t(oe),selection:void 0},redo:{mode:ge,selection:void 0}};t(oe)==="text"&&t(we)&&t(we).flush(),Ne("add history item",qe),t(de).add(qe),v(oe,ge)}})(c())}),F(()=>(t(oe),O(N())),()=>{v(Mn,[{type:"button",text:"text",title:"Switch to text mode (current mode: ".concat(t(oe),")"),className:"jse-group-button jse-first"+(t(oe)===yo.text?" jse-selected":""),onClick:()=>N()(yo.text)},{type:"button",text:"tree",title:"Switch to tree mode (current mode: ".concat(t(oe),")"),className:"jse-group-button "+(t(oe)===yo.tree?" jse-selected":""),onClick:()=>N()(yo.tree)},{type:"button",text:"table",title:"Switch to table mode (current mode: ".concat(t(oe),")"),className:"jse-group-button jse-last"+(t(oe)===yo.table?" jse-selected":""),onClick:()=>N()(yo.table)}])}),F(()=>(t(Mn),O(X()),t(oe),O(y()),O(s())),()=>{v(I,ge=>{var qe=Kp(ge[0])?t(Mn).concat(ge):t(Mn).concat(Y,ge),Ae=Xc(qe);return X()(qe,{mode:t(oe),modal:y(),readOnly:s()})||Ae})}),F(()=>(O(H()),t(oe),O(y()),O(s()),O(r())),()=>{v(ee,ge=>{var qe,Ae=Xc(ge);return(qe=H()(ge,{mode:t(oe),modal:y(),readOnly:s(),selection:r()}))!==null&&qe!==void 0?qe:!s()&&Ae})}),mt(),jn();var Xe=to(),$n=rn(Xe),De=ge=>{Yt(N1(ge,{get externalContent(){return o()},get externalSelection(){return r()},get history(){return t(de)},get readOnly(){return s()},get indentation(){return a()},get tabSize(){return i()},get mainMenuBar(){return d()},get statusBar(){return h()},get askToFormat(){return p()},get escapeUnicodeCharacters(){return g()},get parser(){return j()},get validator(){return k()},get validationParser(){return $()},get onChange(){return R()},get onChangeMode(){return N()},get onSelect(){return _()},onUndo:ce,onRedo:an,get onError(){return ne()},get onFocus(){return ye()},get onBlur(){return te()},get onRenderMenu(){return t(I)},get onSortModal(){return ve()},get onTransformModal(){return Re()},$$legacy:!0}),qe=>v(we,qe),()=>t(we))},Nn=(ge,qe)=>{var Ae=Fn=>{Yt(uy(Fn,{get externalContent(){return o()},get externalSelection(){return r()},get history(){return t(de)},get readOnly(){return s()},get truncateTextSize(){return l()},get mainMenuBar(){return d()},get escapeControlCharacters(){return m()},get escapeUnicodeCharacters(){return g()},get flattenColumns(){return b()},get parser(){return j()},get parseMemoizeOne(){return x()},get validator(){return k()},get validationParser(){return $()},get indentation(){return a()},get onChange(){return R()},get onChangeMode(){return N()},get onSelect(){return _()},onUndo:ce,onRedo:an,get onRenderValue(){return z()},get onFocus(){return ye()},get onBlur(){return te()},get onRenderMenu(){return t(I)},get onRenderContextMenu(){return t(ee)},get onSortModal(){return ve()},get onTransformModal(){return Re()},get onJSONEditorModal(){return Ee()},$$legacy:!0}),gn=>v(Se,gn),()=>t(Se))},Ue=Fn=>{Yt(Ru(Fn,{get externalContent(){return o()},get externalSelection(){return r()},get history(){return t(de)},get readOnly(){return s()},get indentation(){return a()},get truncateTextSize(){return l()},get mainMenuBar(){return d()},get navigationBar(){return u()},get escapeControlCharacters(){return m()},get escapeUnicodeCharacters(){return g()},get parser(){return j()},get parseMemoizeOne(){return x()},get validator(){return k()},get validationParser(){return $()},get pathParser(){return S()},get onError(){return ne()},get onChange(){return R()},get onChangeMode(){return N()},get onSelect(){return _()},onUndo:ce,onRedo:an,get onRenderValue(){return z()},get onClassName(){return W()},get onFocus(){return ye()},get onBlur(){return te()},get onRenderMenu(){return t(I)},get onRenderContextMenu(){return t(ee)},get onSortModal(){return ve()},get onTransformModal(){return Re()},get onJSONEditorModal(){return Ee()},$$legacy:!0}),gn=>v(fe,gn),()=>t(fe))};Q(ge,Fn=>{t(oe)===yo.table?Fn(Ae):Fn(Ue,!1)},qe)};return Q($n,ge=>{t(oe)===yo.text||String(t(oe))==="code"?ge(De):ge(Nn,!1)}),A(e,Xe),hn(n,"patch",P),hn(n,"expand",pe),hn(n,"collapse",K),hn(n,"transform",Me),hn(n,"validate",V),hn(n,"acceptAutoRepair",T),hn(n,"scrollTo",xn),hn(n,"findElement",Qe),hn(n,"focus",cn),hn(n,"refresh",sn),nn({patch:P,expand:pe,collapse:K,transform:Me,validate:V,acceptAutoRepair:T,scrollTo:xn,findElement:Qe,focus:cn,refresh:sn})}fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-modal-wrapper.svelte-v0el4e {
  flex: 1;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: auto;
  min-width: 0;
  min-height: 0;
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-actions:where(.svelte-v0el4e) {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  padding-top: var(--jse-padding, 10px);
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-actions:where(.svelte-v0el4e) button.jse-primary:where(.svelte-v0el4e) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-actions:where(.svelte-v0el4e) button.jse-primary:where(.svelte-v0el4e):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-actions:where(.svelte-v0el4e) button.jse-primary:where(.svelte-v0el4e):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-label:where(.svelte-v0el4e) {
  font-weight: bold;
  display: block;
  box-sizing: border-box;
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-label:where(.svelte-v0el4e) .jse-label-inner:where(.svelte-v0el4e) {
  margin-top: calc(2 * var(--jse-padding, 10px));
  margin-bottom: calc(0.5 * var(--jse-padding, 10px));
  box-sizing: border-box;
}
.jse-modal-wrapper.svelte-v0el4e .jse-modal-contents:where(.svelte-v0el4e) .jse-modal-inline-editor:where(.svelte-v0el4e) {
  flex: 1;
  min-height: 150px;
  min-width: 0;
  max-width: 100%;
  display: flex;
  --jse-theme-color: var(--jse-modal-editor-theme-color, #707070);
  --jse-theme-color-highlight: var(--jse-modal-editor-theme-color-highlight, #646464);
}
.jse-modal-wrapper.svelte-v0el4e .jse-actions:where(.svelte-v0el4e) {
  gap: var(--jse-padding, 10px);
  align-items: center;
}
.jse-modal-wrapper.svelte-v0el4e .jse-actions:where(.svelte-v0el4e) .jse-error:where(.svelte-v0el4e) {
  flex: 1;
  color: var(--jse-error-color, #ee5341);
}
.jse-modal-wrapper.svelte-v0el4e .jse-actions:where(.svelte-v0el4e) button.jse-secondary:where(.svelte-v0el4e) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-secondary-background, #d3d3d3);
  color: var(--jse-button-secondary-color, var(--jse-text-color, #4d4d4d));
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-modal-wrapper.svelte-v0el4e .jse-actions:where(.svelte-v0el4e) button.jse-secondary:where(.svelte-v0el4e):hover {
  background: var(--jse-button-secondary-background-highlight, #e1e1e1);
}
.jse-modal-wrapper.svelte-v0el4e .jse-actions:where(.svelte-v0el4e) button.jse-secondary:where(.svelte-v0el4e):disabled {
  background: var(--jse-button-secondary-background-disabled, #9d9d9d);
}
.jse-modal-wrapper.svelte-v0el4e input:where(.svelte-v0el4e) {
  border: var(--jse-input-border, 1px solid #d8dbdf);
  outline: none;
  box-sizing: border-box;
  padding: calc(0.5 * var(--jse-padding, 10px));
  font-family: var(--jse-font-family-mono, consolas, menlo, monaco, "Ubuntu Mono", "source-code-pro", monospace);
  font-size: var(--jse-font-size-mono, 14px);
  color: inherit;
  background: var(--jse-input-background, var(--jse-background-color, #fff));
}
.jse-modal-wrapper.svelte-v0el4e input:where(.svelte-v0el4e):focus {
  border: var(--jse-input-border-focus, 1px solid var(--jse-input-border-focus, var(--jse-theme-color, #3883fa)));
}
.jse-modal-wrapper.svelte-v0el4e input:where(.svelte-v0el4e):read-only {
  background: var(--jse-input-background-readonly, transparent);
}`);var dy=L('<div class="jse-error svelte-v0el4e"> </div>'),vy=L('<button type="button" class="jse-secondary svelte-v0el4e"><!> Back</button>'),fy=L('<button type="button" class="jse-primary svelte-v0el4e">Apply</button>'),py=L('<button type="button" class="jse-primary svelte-v0el4e">Close</button>'),hy=L('<!> <div class="jse-modal-contents svelte-v0el4e"><div class="jse-label svelte-v0el4e"><div class="jse-label-inner svelte-v0el4e">Path</div></div> <input class="jse-path svelte-v0el4e" type="text" readonly="" title="Selected path"> <div class="jse-label svelte-v0el4e"><div class="jse-label-inner svelte-v0el4e">Contents</div></div> <div class="jse-modal-inline-editor svelte-v0el4e"><!></div> <div class="jse-actions svelte-v0el4e"><!> <!> <!></div></div>',1),gy=L('<div class="jse-modal-wrapper svelte-v0el4e"><!></div>'),my={};fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-modal-contents.svelte-1v9c92j {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: auto;
  min-width: 0;
  min-height: 0;
}
.jse-modal-contents.svelte-1v9c92j .jse-actions:where(.svelte-1v9c92j) {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  padding-top: var(--jse-padding, 10px);
}
.jse-modal-contents.svelte-1v9c92j .jse-actions:where(.svelte-1v9c92j) button.jse-primary:where(.svelte-1v9c92j) {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  padding: 5px;
  margin: 0;
  background: var(--jse-button-primary-background, var(--jse-theme-color, #3883fa));
  color: var(--jse-button-primary-color, #fff);
  padding: var(--jse-padding, 10px) calc(2 * var(--jse-padding, 10px));
  border-radius: 3px;
}
.jse-modal-contents.svelte-1v9c92j .jse-actions:where(.svelte-1v9c92j) button.jse-primary:where(.svelte-1v9c92j):hover {
  background: var(--jse-button-primary-background-highlight, var(--jse-theme-color-highlight, #5f9dff));
}
.jse-modal-contents.svelte-1v9c92j .jse-actions:where(.svelte-1v9c92j) button.jse-primary:where(.svelte-1v9c92j):disabled {
  background: var(--jse-button-primary-background-disabled, #9d9d9d);
}
.jse-modal-contents.svelte-1v9c92j table:where(.svelte-1v9c92j) {
  width: 100%;
  border-collapse: collapse;
  border-spacing: 0;
}
.jse-modal-contents.svelte-1v9c92j table:where(.svelte-1v9c92j) th:where(.svelte-1v9c92j),
.jse-modal-contents.svelte-1v9c92j table:where(.svelte-1v9c92j) td:where(.svelte-1v9c92j) {
  text-align: left;
  vertical-align: middle;
  font-weight: normal;
  padding-bottom: var(--jse-padding, 10px);
}
.jse-modal-contents.svelte-1v9c92j input.jse-path:where(.svelte-1v9c92j) {
  width: 100%;
  box-sizing: border-box;
  padding: 5px 10px;
  border: var(--jse-input-border, 1px solid #d8dbdf);
  border-radius: var(--jse-input-radius, 3px);
  font-family: inherit;
  font-size: inherit;
  background: inherit;
  background: var(--jse-input-background-readonly, transparent);
  color: inherit;
  outline: none;
}
.jse-modal-contents.svelte-1v9c92j .svelte-select input {
  box-sizing: border-box;
}
.jse-modal-contents.svelte-1v9c92j .jse-space:where(.svelte-1v9c92j) {
  height: 200px;
}
.jse-modal-contents.svelte-1v9c92j .jse-space:where(.svelte-1v9c92j) .jse-error:where(.svelte-1v9c92j) {
  color: var(--jse-error-color, #ee5341);
}`);var ss=Gl(()=>my),by=L('<tr><th class="svelte-1v9c92j">Property</th><td class="svelte-1v9c92j"><!></td></tr>'),jy=L('<div class="jse-error svelte-1v9c92j"> </div>'),xy=L('<!> <div class="jse-modal-contents svelte-1v9c92j"><table class="svelte-1v9c92j"><colgroup><col width="25%"><col width="75%"></colgroup><tbody><tr><th class="svelte-1v9c92j">Path</th><td class="svelte-1v9c92j"><input class="jse-path svelte-1v9c92j" type="text" readonly="" title="Selected path"></td></tr><!><tr><th class="svelte-1v9c92j">Direction</th><td class="svelte-1v9c92j"><!></td></tr></tbody></table> <div class="jse-space svelte-1v9c92j"><!></div> <div class="jse-actions svelte-1v9c92j"><button type="button" class="jse-primary svelte-1v9c92j">Sort</button></div></div>',1);fn(`/* over all fonts, sizes, and colors */
/* "consolas" for Windows, "menlo" for Mac with fallback to "monaco", 'Ubuntu Mono' for Ubuntu */
/* (at Mac this font looks too large at 14px, but 13px is too small for the font on Windows) */
/* main, menu, modal */
/* jsoneditor modal */
/* tooltip in text mode */
/* panels: navigation bar, gutter, search box */
/* navigation-bar */
/* context menu */
/* contents: json key and values */
/* contents: selected or hovered */
/* contents: section of collapsed items in an array */
/* contents: highlighting of search matches */
/* contents: inline tags inside the JSON document */
/* contents: table */
/* controls in modals: inputs, buttons, and \`a\` */
/* messages */
/* svelte-select */
/* color picker */
.jse-main.svelte-57bmz4 {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 150px;
  font-family: var(--jse-font-family, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen-Sans, Ubuntu, Cantarell, "Helvetica Neue", sans-serif);
  font-size: var(--jse-font-size, 16px);
  line-height: normal;
  position: relative;
  display: flex;
  flex-direction: row;
}
.jse-main.svelte-57bmz4:not(.jse-focus) {
  --jse-selection-background-color: var(--jse-selection-background-inactive-color, #e8e8e8);
  --jse-context-menu-pointer-background: var(--jse-context-menu-pointer-hover-background, #b2b2b2);
}`);var yy=L('<div role="none"><!></div> <!> <!> <!>',1);function wy(e,n){en(n,!1);var o=E(void 0,!0),r=So("jsoneditor:JSONEditor"),s={text:""},a=void 0,i=!1,l=yo.tree,c=!0,d=!0,u=!0,h=!0,p=!1,m=!1,g=!0,b=JSON,j=void 0,x=JSON,k={parse:R0,stringify:vr},$=[Kb],S=$[0].id,y=tr,R=void 0,N=void 0,_=A0,z=tr,W=tr,X=tr,H=tr,ne=le=>{console.error(le),alert(le.toString())},ye=tr,te=tr,ve=f(n,"content",13,s),Re=f(n,"selection",13,a),Ee=f(n,"readOnly",13,i),fe=f(n,"indentation",13,2),Se=f(n,"tabSize",13,4),we=f(n,"truncateTextSize",13,1e3),Ne=f(n,"mode",13,l),de=f(n,"mainMenuBar",13,c),oe=f(n,"navigationBar",13,d),ce=f(n,"statusBar",13,u),an=f(n,"askToFormat",13,h),Mn=f(n,"escapeControlCharacters",13,p),Y=f(n,"escapeUnicodeCharacters",13,m),I=f(n,"flattenColumns",13,g),ee=f(n,"parser",13,b),P=f(n,"validator",13,j),pe=f(n,"validationParser",13,x),K=f(n,"pathParser",13,k),Me=f(n,"queryLanguages",13,$),V=f(n,"queryLanguageId",13,S),T=f(n,"onChangeQueryLanguage",13,y),xn=f(n,"onChange",13,R),Qe=f(n,"onSelect",13,N),cn=f(n,"onRenderValue",13,_),sn=f(n,"onClassName",13,z),je=f(n,"onRenderMenu",13,W),Xe=f(n,"onRenderContextMenu",13,X),$n=f(n,"onChangeMode",13,H),De=f(n,"onError",13,ne),Nn=f(n,"onFocus",13,ye),ge=f(n,"onBlur",13,te),qe=E(us(),!0),Ae=E(!1,!0),Ue=E(void 0,!0),Fn=E(void 0,!0),gn=E(void 0,!0),Ve=E(void 0,!0),D=E(ee(),!0);function Ke(){return ve()}function Ge(le){r("set");var Gn=Mc(le);if(Gn)throw new Error(Gn);v(qe,us()),ve(le),Gt()}function Ct(le){r("update");var Gn=Mc(le);if(Gn)throw new Error(Gn);ve(le),Gt()}function _e(le){var Gn=t(Ue).patch(le);return Gt(),Gn}function Rn(le){Re(le),Gt()}function Un(le,Gn){t(Ue).expand(le,Gn),Gt()}function co(le){var Gn=arguments.length>1&&arguments[1]!==void 0&&arguments[1];t(Ue).collapse(le,Gn),Gt()}function ut(){var le=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{};t(Ue).transform(le),Gt()}function _n(){return t(Ue).validate()}function Lt(){var le=t(Ue).acceptAutoRepair();return Gt(),le}function Qn(le){return ft.apply(this,arguments)}function ft(){return(ft=bn(function*(le){yield t(Ue).scrollTo(le)})).apply(this,arguments)}function rt(le){return t(Ue).findElement(le)}function Ln(){t(Ue).focus(),Gt()}function yn(){return st.apply(this,arguments)}function st(){return(st=bn(function*(){yield t(Ue).refresh()})).apply(this,arguments)}function on(le){var Gn,B,ke,He,zn,We,Tn,dt,On,pt,un,En,tn,pn,St,At,Dn,Pn,qn,Jt,U,Z,he,An,it,Xn,jt,Ut,qt,Bt,mn,_t=Object.keys(le);for(var vn of _t)switch(vn){case"content":ve((Gn=le[vn])!==null&&Gn!==void 0?Gn:s);break;case"selection":Re((B=le[vn])!==null&&B!==void 0?B:a);break;case"readOnly":Ee((ke=le[vn])!==null&&ke!==void 0?ke:i);break;case"indentation":fe((He=le[vn])!==null&&He!==void 0?He:2);break;case"tabSize":Se((zn=le[vn])!==null&&zn!==void 0?zn:4);break;case"truncateTextSize":we((We=le[vn])!==null&&We!==void 0?We:1e3);break;case"mode":Ne((Tn=le[vn])!==null&&Tn!==void 0?Tn:l);break;case"mainMenuBar":de((dt=le[vn])!==null&&dt!==void 0?dt:c);break;case"navigationBar":oe((On=le[vn])!==null&&On!==void 0?On:d);break;case"statusBar":ce((pt=le[vn])!==null&&pt!==void 0?pt:u);break;case"askToFormat":an((un=le[vn])!==null&&un!==void 0?un:h);break;case"escapeControlCharacters":Mn((En=le[vn])!==null&&En!==void 0?En:p);break;case"escapeUnicodeCharacters":Y((tn=le[vn])!==null&&tn!==void 0?tn:m);break;case"flattenColumns":I((pn=le[vn])!==null&&pn!==void 0?pn:g);break;case"parser":ee((St=le[vn])!==null&&St!==void 0?St:b);break;case"validator":P((At=le[vn])!==null&&At!==void 0?At:j);break;case"validationParser":pe((Dn=le[vn])!==null&&Dn!==void 0?Dn:x);break;case"pathParser":K((Pn=le[vn])!==null&&Pn!==void 0?Pn:k);break;case"queryLanguages":Me((qn=le[vn])!==null&&qn!==void 0?qn:$);break;case"queryLanguageId":V((Jt=le[vn])!==null&&Jt!==void 0?Jt:S);break;case"onChangeQueryLanguage":T((U=le[vn])!==null&&U!==void 0?U:y);break;case"onChange":xn((Z=le[vn])!==null&&Z!==void 0?Z:R);break;case"onRenderValue":cn((he=le[vn])!==null&&he!==void 0?he:_);break;case"onClassName":sn((An=le[vn])!==null&&An!==void 0?An:z);break;case"onRenderMenu":je((it=le[vn])!==null&&it!==void 0?it:W);break;case"onRenderContextMenu":Xe((Xn=le[vn])!==null&&Xn!==void 0?Xn:X);break;case"onChangeMode":$n((jt=le[vn])!==null&&jt!==void 0?jt:H);break;case"onSelect":Qe((Ut=le[vn])!==null&&Ut!==void 0?Ut:N);break;case"onError":De((qt=le[vn])!==null&&qt!==void 0?qt:ne);break;case"onFocus":Nn((Bt=le[vn])!==null&&Bt!==void 0?Bt:ye);break;case"onBlur":ge((mn=le[vn])!==null&&mn!==void 0?mn:te);break;default:xo(vn)}function xo($e){r('Unknown property "'.concat($e,'"'))}Me().some($e=>$e.id===V())||V(Me()[0].id),Gt()}function Jn(){return Wn.apply(this,arguments)}function Wn(){return(Wn=bn(function*(){throw new Error("class method destroy() is deprecated. It is replaced with a method destroy() in the vanilla library.")})).apply(this,arguments)}function Et(le,Gn,B){ve(le),xn()&&xn()(le,Gn,B)}function Kt(le){Re(le),Qe()&&Qe()(Xc(le))}function bt(){v(Ae,!0),Nn()&&Nn()()}function vo(){v(Ae,!1),ge()&&ge()()}function oo(le){return uo.apply(this,arguments)}function uo(){return(uo=bn(function*(le){Ne()!==le&&(Ne(le),Gt(),Ln(),$n()(le))})).apply(this,arguments)}function jo(le){r("handleChangeQueryLanguage",le),V(le),T()(le)}function fo(le){var{id:Gn,json:B,rootPath:ke,onTransform:He,onClose:zn}=le;Ee()||v(Ve,{id:Gn,json:B,rootPath:ke,indentation:fe(),truncateTextSize:we(),escapeControlCharacters:Mn(),escapeUnicodeCharacters:Y(),parser:ee(),parseMemoizeOne:t(o),validationParser:pe(),pathParser:K(),queryLanguages:Me(),queryLanguageId:V(),onChangeQueryLanguage:jo,onRenderValue:cn(),onRenderMenu:We=>je()(We,{mode:Ne(),modal:!0,readOnly:Ee()}),onRenderContextMenu:We=>Xe()(We,{mode:Ne(),modal:!0,readOnly:Ee(),selection:Re()}),onClassName:sn(),onTransform:He,onClose:zn})}function Pt(le){Ee()||v(gn,le)}function It(le){var{content:Gn,path:B,onPatch:ke,onClose:He}=le;r("onJSONEditorModal",{content:Gn,path:B}),v(Fn,{content:Gn,path:B,onPatch:ke,readOnly:Ee(),indentation:fe(),tabSize:Se(),truncateTextSize:we(),mainMenuBar:de(),navigationBar:oe(),statusBar:ce(),askToFormat:an(),escapeControlCharacters:Mn(),escapeUnicodeCharacters:Y(),flattenColumns:I(),parser:ee(),validator:void 0,validationParser:pe(),pathParser:K(),onRenderValue:cn(),onClassName:sn(),onRenderMenu:je(),onRenderContextMenu:Xe(),onSortModal:Pt,onTransformModal:fo,onClose:He})}function Zt(le){le.stopPropagation()}return F(()=>(O(ee()),t(D),O(ve()),us),()=>{if(!Ib(ee(),t(D))){if(r("parser changed, recreate editor"),mi(ve())){var le=t(D).stringify(ve().json);ve({json:le!==void 0?ee().parse(le):void 0})}v(D,ee()),v(qe,us())}}),F(()=>O(ve()),()=>{var le=Mc(ve());le&&console.error("Error: "+le)}),F(()=>O(Re()),()=>{Re()===null&&console.warn("selection is invalid: it is null but should be undefined")}),F(()=>O(ee()),()=>{v(o,vi(ee().parse))}),F(()=>O(Ne()),()=>{r("mode changed to",Ne())}),mt(),jn(!0),fu(e,{children:(le,Gn)=>{var B,ke=yy(),He=rn(ke);zp(M(He),()=>t(qe),un=>{Yt(Zv(un,{get externalMode(){return Ne()},get content(){return ve()},get selection(){return Re()},get readOnly(){return Ee()},get indentation(){return fe()},get tabSize(){return Se()},get truncateTextSize(){return we()},get statusBar(){return ce()},get askToFormat(){return an()},get mainMenuBar(){return de()},get navigationBar(){return oe()},get escapeControlCharacters(){return Mn()},get escapeUnicodeCharacters(){return Y()},get flattenColumns(){return I()},get parser(){return ee()},get parseMemoizeOne(){return t(o)},get validator(){return P()},get validationParser(){return pe()},get pathParser(){return K()},insideModal:!1,get onError(){return De()},onChange:Et,onChangeMode:oo,onSelect:Kt,get onRenderValue(){return cn()},get onClassName(){return sn()},onFocus:bt,onBlur:vo,get onRenderMenu(){return je()},get onRenderContextMenu(){return Xe()},onSortModal:Pt,onTransformModal:fo,onJSONEditorModal:It,$$legacy:!0}),En=>v(Ue,En),()=>t(Ue))});var zn=q(He,2),We=un=>{(function(En,tn){var pn,St;en(tn,!1);var At=E(void 0,!0),Dn=E(void 0,!0),Pn=E(void 0,!0),qn=E(void 0,!0),Jt=So("jsoneditor:SortModal"),U=f(tn,"id",9),Z=f(tn,"json",9),he=f(tn,"rootPath",9),An=f(tn,"onSort",9),it=f(tn,"onClose",9),Xn={value:1,label:"ascending"},jt=[Xn,{value:-1,label:"descending"}],Ut="".concat(U(),":").concat(Sn(he())),qt=E((pn=ss()[Ut])===null||pn===void 0?void 0:pn.selectedProperty,!0),Bt=E(((St=ss()[Ut])===null||St===void 0?void 0:St.selectedDirection)||Xn,!0),mn=E(void 0,!0);function _t(){try{var xo,$e,at;v(mn,void 0);var zt=((xo=t(qt))===null||xo===void 0?void 0:xo.value)||(($e=t(qn))===null||$e===void 0||($e=$e[0])===null||$e===void 0?void 0:$e.value)||[],lt=(at=t(Bt))===null||at===void 0?void 0:at.value,ro=Mh(Z(),he(),zt,lt);An()!==void 0&&he()!==void 0&&An()({operations:ro,rootPath:he(),itemPath:zt,direction:lt}),it()()}catch(_o){v(mn,String(_o))}}function vn(xo){xo.focus()}F(()=>(O(Z()),O(he())),()=>{v(At,Je(Z(),he()))}),F(()=>t(At),()=>{v(Dn,Array.isArray(t(At)))}),F(()=>(t(Dn),t(At)),()=>{v(Pn,t(Dn)?uu(t(At)):void 0)}),F(()=>(t(Pn),fa),()=>{v(qn,t(Pn)?t(Pn).map(fa):void 0)}),F(()=>(ss(),t(qt),t(Bt)),()=>{ss(ss()[Ut]={selectedProperty:t(qt),selectedDirection:t(Bt)}),Jt("store state in memory",Ut,ss()[Ut])}),mt(),jn(!0),zi(En,{get onClose(){return it()},className:"jse-sort-modal",children:(xo,$e)=>{var at=xy(),zt=rn(at),lt=ae(()=>t(Dn)?"Sort array items":"Sort object keys");Pl(zt,{get title(){return t(lt)},get onClose(){return it()}});var ro=M(q(zt,2)),_o=q(M(ro)),C=M(_o),G=q(M(C)),re=M(G),ue=q(C),xe=Cn=>{var Rt=by(),w=q(M(Rt));Ta(M(w),{showChevron:!0,get items(){return t(qn)},get value(){return t(qt)},set value(J){v(qt,J)},$$legacy:!0}),A(Cn,Rt)};Q(ue,Cn=>{var Rt;t(Dn)&&(t(qn)&&((Rt=t(qn))===null||Rt===void 0?void 0:Rt.length)>1||t(qt)===void 0)&&Cn(xe)});var Te=q(ue),Pe=q(M(Te));Ta(M(Pe),{showChevron:!0,clearable:!1,items:jt,get value(){return t(Bt)},set value(Cn){v(Bt,Cn)},$$legacy:!0});var Ze=q(ro,2),Bn=M(Ze),Zn=Cn=>{var Rt=jy(),w=M(Rt);be(()=>ln(w,t(mn))),A(Cn,Rt)};Q(Bn,Cn=>{t(mn)&&Cn(Zn)});var Vn=M(q(Ze,2));$o(()=>ie("click",Vn,_t)),Uo(Vn,Cn=>vn==null?void 0:vn(Cn)),be(Cn=>{var Rt;La(re,Cn),Vn.disabled=!!(t(Dn)&&t(qn)&&((Rt=t(qn))===null||Rt===void 0?void 0:Rt.length)>1)&&!t(qt)},[()=>he()&&!Mt(he())?vr(he()):"(document root)"],ae),A(xo,at)},$$slots:{default:!0}}),nn()})(un,ya(()=>t(gn),{onClose:()=>{var En;(En=t(gn))===null||En===void 0||En.onClose(),v(gn,void 0)}}))};Q(zn,un=>{t(gn)&&un(We)});var Tn=q(zn,2),dt=un=>{C1(un,ya(()=>t(Ve),{onClose:()=>{var En;(En=t(Ve))===null||En===void 0||En.onClose(),v(Ve,void 0)}}))};Q(Tn,un=>{t(Ve)&&un(dt)});var On=q(Tn,2),pt=un=>{(function(En,tn){en(tn,!1);var pn=E(void 0,!0),St=E(void 0,!0),At=E(void 0,!0),Dn=E(void 0,!0),Pn=So("jsoneditor:JSONEditorModal"),qn=f(tn,"content",9),Jt=f(tn,"path",9),U=f(tn,"onPatch",9),Z=f(tn,"readOnly",9),he=f(tn,"indentation",9),An=f(tn,"tabSize",9),it=f(tn,"truncateTextSize",9),Xn=f(tn,"mainMenuBar",9),jt=f(tn,"navigationBar",9),Ut=f(tn,"statusBar",9),qt=f(tn,"askToFormat",9),Bt=f(tn,"escapeControlCharacters",9),mn=f(tn,"escapeUnicodeCharacters",9),_t=f(tn,"flattenColumns",9),vn=f(tn,"parser",9),xo=f(tn,"validator",9),$e=f(tn,"validationParser",9),at=f(tn,"pathParser",9),zt=f(tn,"onRenderValue",9),lt=f(tn,"onClassName",9),ro=f(tn,"onRenderMenu",9),_o=f(tn,"onRenderContextMenu",9),C=f(tn,"onSortModal",9),G=f(tn,"onTransformModal",9),re=f(tn,"onClose",9),ue=E(void 0,!0),xe=E(void 0,!0),Te={mode:Bn(qn()),content:qn(),selection:void 0,relativePath:Jt()},Pe=E([Te],!0),Ze=E(void 0,!0);function Bn(ze){return mi(ze)&&wo(ze.json)?yo.table:yo.tree}function Zn(){var ze,Fe=(ze=Kn(t(Pe)))===null||ze===void 0?void 0:ze.selection;wi(Fe)&&t(ue).scrollTo(Be(Fe))}function Vn(){if(Pn("handleApply"),!Z())try{v(Ze,void 0);var ze=t(pn).relativePath,Fe=t(pn).content,wn=[{op:"replace",path:Sn(ze),value:gv(Fe,vn()).json}];if(t(Pe).length>1){var tt=gv(t(Pe)[t(Pe).length-2].content,vn()).json,Hn={json:zr(tt,wn)},Ot=me(me({},t(Pe)[t(Pe).length-2]||Te),{},{content:Hn});v(Pe,[...t(Pe).slice(0,t(Pe).length-2),Ot]),Gt(),Zn()}else U()(wn),re()()}catch(dn){v(Ze,String(dn))}}function Cn(){if(Pn("handleClose"),t(xe))v(xe,!1);else if(t(Pe).length>1){var ze;v(Pe,ot(t(Pe))),Gt(),(ze=t(ue))===null||ze===void 0||ze.focus(),Zn(),v(Ze,void 0)}else re()()}function Rt(ze){Pn("handleChange",ze),se(Fe=>me(me({},Fe),{},{content:ze}))}function w(ze){Pn("handleChangeSelection",ze),se(Fe=>me(me({},Fe),{},{selection:ze}))}function J(ze){Pn("handleChangeMode",ze),se(Fe=>me(me({},Fe),{},{mode:ze}))}function se(ze){var Fe=ze(Kn(t(Pe)));v(Pe,[...ot(t(Pe)),Fe])}function Ie(ze){v(Ze,ze.toString()),console.error(ze)}function Oe(ze){var Fe,{content:wn,path:tt}=ze;Pn("handleJSONEditorModal",{content:wn,path:tt});var Hn={mode:Bn(wn),content:wn,selection:void 0,relativePath:tt};v(Pe,[...t(Pe),Hn]),Gt(),(Fe=t(ue))===null||Fe===void 0||Fe.focus()}function Ce(ze){ze.focus()}Po(()=>{var ze;(ze=t(ue))===null||ze===void 0||ze.focus()}),F(()=>t(Pe),()=>{v(pn,Kn(t(Pe))||Te)}),F(()=>t(Pe),()=>{v(St,t(Pe).flatMap(ze=>ze.relativePath))}),F(()=>(t(St),vr),()=>{v(At,Mt(t(St))?"(document root)":vr(t(St)))}),F(()=>O(vn()),()=>{v(Dn,vi(vn().parse))}),mt(),jn(!0),zi(En,{onClose:Cn,className:"jse-jsoneditor-modal",get fullscreen(){return t(xe)},children:(ze,Fe)=>{var wn=gy();fu(M(wn),{children:(tt,Hn)=>{var Ot=hy(),dn=rn(Ot),eo=ae(()=>t(Pe).length>1?" (".concat(t(Pe).length,")"):"");Pl(dn,{get title(){var In;return"Edit nested content ".concat((In=t(eo))!==null&&In!==void 0?In:"")},fullScreenButton:!0,onClose:Cn,get fullscreen(){return t(xe)},set fullscreen(In){v(xe,In)},$$legacy:!0});var Tt=q(dn,2),Dt=q(M(Tt),2),ct=q(Dt,4);Yt(Zv(M(ct),{get externalMode(){return t(pn).mode},get content(){return t(pn).content},get selection(){return t(pn).selection},get readOnly(){return Z()},get indentation(){return he()},get tabSize(){return An()},get truncateTextSize(){return it()},get statusBar(){return Ut()},get askToFormat(){return qt()},get mainMenuBar(){return Xn()},get navigationBar(){return jt()},get escapeControlCharacters(){return Bt()},get escapeUnicodeCharacters(){return mn()},get flattenColumns(){return _t()},get parser(){return vn()},get parseMemoizeOne(){return t(Dn)},get validator(){return xo()},get validationParser(){return $e()},get pathParser(){return at()},insideModal:!0,onError:Ie,onChange:Rt,onChangeMode:J,onSelect:w,get onRenderValue(){return zt()},get onClassName(){return lt()},onFocus:tr,onBlur:tr,get onRenderMenu(){return ro()},get onRenderContextMenu(){return _o()},get onSortModal(){return C()},get onTransformModal(){return G()},onJSONEditorModal:Oe,$$legacy:!0}),In=>v(ue,In),()=>t(ue));var Le=M(q(ct,2)),Ye=In=>{var go=dy(),Ao=M(go);be(()=>ln(Ao,t(Ze))),A(In,go)};Q(Le,In=>{t(Ze)&&In(Ye)});var vt=q(Le,2),Qt=In=>{var go=vy();nt(M(go),{data:sm}),ie("click",go,Cn),A(In,go)};Q(vt,In=>{t(Pe).length>1&&In(Qt)});var ao=q(vt,2),yt=In=>{var go=fy();$o(()=>ie("click",go,Vn)),Uo(go,Ao=>Ce==null?void 0:Ce(Ao)),A(In,go)},xt=In=>{var go=py();ie("click",go,Cn),A(In,go)};Q(ao,In=>{Z()?In(xt,!1):In(yt)}),be(()=>La(Dt,t(At))),A(tt,Ot)},$$slots:{default:!0}}),A(ze,wn)},$$slots:{default:!0}}),nn()})(un,ya(()=>t(Fn),{onClose:()=>{var En;(En=t(Fn))===null||En===void 0||En.onClose(),v(Fn,void 0)}}))};Q(On,un=>{t(Fn)&&un(pt)}),be(un=>B=kn(He,1,"jse-main svelte-57bmz4",null,B,un),[()=>({"jse-focus":t(Ae)})],ae),ie("keydown",He,Zt),A(le,ke)},$$slots:{default:!0}}),hn(n,"get",Ke),hn(n,"set",Ge),hn(n,"update",Ct),hn(n,"patch",_e),hn(n,"select",Rn),hn(n,"expand",Un),hn(n,"collapse",co),hn(n,"transform",ut),hn(n,"validate",_n),hn(n,"acceptAutoRepair",Lt),hn(n,"scrollTo",Qn),hn(n,"findElement",rt),hn(n,"focus",Ln),hn(n,"refresh",yn),hn(n,"updateProps",on),hn(n,"destroy",Jn),nn({get:Ke,set:Ge,update:Ct,patch:_e,select:Rn,expand:Un,collapse:co,transform:ut,validate:_n,acceptAutoRepair:Lt,scrollTo:Qn,findElement:rt,focus:Ln,refresh:yn,updateProps:on,destroy:Jn})}function ky(e){var{target:n,props:o}=e,r=hb(wy,{target:n,props:o});return r.destroy=bn(function*(){return function(s,a){var i=lu.get(s);return i?(lu.delete(s),i(a)):Promise.resolve()}(r)}),Gt(),r}const Cy=/"(?:_|\\u0{2}5[Ff]){2}(?:p|\\u0{2}70)(?:r|\\u0{2}72)(?:o|\\u0{2}6[Ff])(?:t|\\u0{2}74)(?:o|\\u0{2}6[Ff])(?:_|\\u0{2}5[Ff]){2}"\s*:/,Sy=/"(?:c|\\u0063)(?:o|\\u006[Ff])(?:n|\\u006[Ee])(?:s|\\u0073)(?:t|\\u0074)(?:r|\\u0072)(?:u|\\u0075)(?:c|\\u0063)(?:t|\\u0074)(?:o|\\u006[Ff])(?:r|\\u0072)"\s*:/,zy=/^\s*["[{]|^\s*-?\d{1,16}(\.\d{1,17})?([Ee][+-]?\d+)?\s*$/;function Oy(e,n){if(e==="__proto__"||e==="constructor"&&n&&typeof n=="object"&&"prototype"in n){My(e);return}return n}function My(e){console.warn(`[destr] Dropping "${e}" key to prevent prototype pollution.`)}function cl(e,n={}){if(typeof e!="string")return e;const o=e.trim();if(e[0]==='"'&&e.endsWith('"')&&!e.includes("\\"))return o.slice(1,-1);if(o.length<=9){const r=o.toLowerCase();if(r==="true")return!0;if(r==="false")return!1;if(r==="undefined")return;if(r==="null")return null;if(r==="nan")return Number.NaN;if(r==="infinity")return Number.POSITIVE_INFINITY;if(r==="-infinity")return Number.NEGATIVE_INFINITY}if(!zy.test(e)){if(n.strict)throw new SyntaxError("[destr] Invalid JSON");return e}try{if(Cy.test(e)||Sy.test(e)){if(n.strict)throw new Error("[destr] Possible prototype pollution");return JSON.parse(e,Oy)}return JSON.parse(e)}catch(r){if(n.strict)throw r;return e}}function $y(e,n={}){return cl(e,{...n,strict:!0})}var Ey=typeof global=="object"&&global&&global.Object===Object&&global,Ay=typeof self=="object"&&self&&self.Object===Object&&self,Fh=Ey||Ay||Function("return this")(),Nl=Fh.Symbol,Lh=Object.prototype,Ry=Lh.hasOwnProperty,Ty=Lh.toString,ti=Nl?Nl.toStringTag:void 0;function Py(e){var n=Ry.call(e,ti),o=e[ti];try{e[ti]=void 0;var r=!0}catch{}var s=Ty.call(e);return r&&(n?e[ti]=o:delete e[ti]),s}var Iy=Object.prototype,qy=Iy.toString;function Ny(e){return qy.call(e)}var Uy="[object Null]",_y="[object Undefined]",ef=Nl?Nl.toStringTag:void 0;function Dy(e){return e==null?e===void 0?_y:Uy:ef&&ef in Object(e)?Py(e):Ny(e)}function Fy(e){return e!=null&&typeof e=="object"}var Ly="[object Symbol]";function Jy(e){return typeof e=="symbol"||Fy(e)&&Dy(e)==Ly}var By=/\s/;function Wy(e){for(var n=e.length;n--&&By.test(e.charAt(n)););return n}var Vy=/^\s+/;function Hy(e){return e&&e.slice(0,Wy(e)+1).replace(Vy,"")}function Tu(e){var n=typeof e;return e!=null&&(n=="object"||n=="function")}var nf=NaN,Yy=/^[-+]0x[0-9a-f]+$/i,Ky=/^0b[01]+$/i,Qy=/^0o[0-7]+$/i,Gy=parseInt;function tf(e){if(typeof e=="number")return e;if(Jy(e))return nf;if(Tu(e)){var n=typeof e.valueOf=="function"?e.valueOf():e;e=Tu(n)?n+"":n}if(typeof e!="string")return e===0?e:+e;e=Hy(e);var o=Ky.test(e);return o||Qy.test(e)?Gy(e.slice(2),o?2:8):Yy.test(e)?nf:+e}var Yc=function(){return Fh.Date.now()},Xy="Expected a function",Zy=Math.max,ew=Math.min;function nw(e,n,o){var r,s,a,i,l,c,d=0,u=!1,h=!1,p=!0;if(typeof e!="function")throw new TypeError(Xy);n=tf(n)||0,Tu(o)&&(u=!!o.leading,h="maxWait"in o,a=h?Zy(tf(o.maxWait)||0,n):a,p="trailing"in o?!!o.trailing:p);function m(R){var N=r,_=s;return r=s=void 0,d=R,i=e.apply(_,N),i}function g(R){return d=R,l=setTimeout(x,n),u?m(R):i}function b(R){var N=R-c,_=R-d,z=n-N;return h?ew(z,a-_):z}function j(R){var N=R-c,_=R-d;return c===void 0||N>=n||N<0||h&&_>=a}function x(){var R=Yc();if(j(R))return k(R);l=setTimeout(x,b(R))}function k(R){return l=void 0,p&&r?m(R):(r=s=void 0,i)}function $(){l!==void 0&&clearTimeout(l),d=0,r=c=s=l=void 0}function S(){return l===void 0?i:k(Yc())}function y(){var R=Yc(),N=j(R);if(r=arguments,s=this,c=R,N){if(l===void 0)return g(c);if(h)return clearTimeout(l),l=setTimeout(x,n),m(c)}return l===void 0&&(l=setTimeout(x,n)),i}return y.cancel=$,y.flush=S,y}var Jh=typeof global=="object"&&global&&global.Object===Object&&global,tw=typeof self=="object"&&self&&self.Object===Object&&self,Nr=Jh||tw||Function("return this")(),Ir=Nr.Symbol,Bh=Object.prototype,ow=Bh.hasOwnProperty,rw=Bh.toString,oi=Ir?Ir.toStringTag:void 0;function aw(e){var n=ow.call(e,oi),o=e[oi];try{e[oi]=void 0;var r=!0}catch{}var s=rw.call(e);return r&&(n?e[oi]=o:delete e[oi]),s}var sw=Object.prototype,iw=sw.toString;function lw(e){return iw.call(e)}var cw="[object Null]",uw="[object Undefined]",of=Ir?Ir.toStringTag:void 0;function Ka(e){return e==null?e===void 0?uw:cw:of&&of in Object(e)?aw(e):lw(e)}function qr(e){return e!=null&&typeof e=="object"}var dw="[object Symbol]";function kd(e){return typeof e=="symbol"||qr(e)&&Ka(e)==dw}function vw(e,n){for(var o=-1,r=e==null?0:e.length,s=Array(r);++o<r;)s[o]=n(e[o],o,e);return s}var ar=Array.isArray,rf=Ir?Ir.prototype:void 0,af=rf?rf.toString:void 0;function Wh(e){if(typeof e=="string")return e;if(ar(e))return vw(e,Wh)+"";if(kd(e))return af?af.call(e):"";var n=e+"";return n=="0"&&1/e==-1/0?"-0":n}function Ur(e){var n=typeof e;return e!=null&&(n=="object"||n=="function")}function Cd(e){return e}var fw="[object AsyncFunction]",pw="[object Function]",hw="[object GeneratorFunction]",gw="[object Proxy]";function Sd(e){if(!Ur(e))return!1;var n=Ka(e);return n==pw||n==hw||n==fw||n==gw}var Kc=Nr["__core-js_shared__"],sf=function(){var e=/[^.]+$/.exec(Kc&&Kc.keys&&Kc.keys.IE_PROTO||"");return e?"Symbol(src)_1."+e:""}();function mw(e){return!!sf&&sf in e}var bw=Function.prototype,jw=bw.toString;function Qa(e){if(e!=null){try{return jw.call(e)}catch{}try{return e+""}catch{}}return""}var xw=/[\\^$.*+?()[\]{}|]/g,yw=/^\[object .+?Constructor\]$/,ww=Function.prototype,kw=Object.prototype,Cw=ww.toString,Sw=kw.hasOwnProperty,zw=RegExp("^"+Cw.call(Sw).replace(xw,"\\$&").replace(/hasOwnProperty|(function).*?(?=\\\()| for .+?(?=\\\])/g,"$1.*?")+"$");function Ow(e){if(!Ur(e)||mw(e))return!1;var n=Sd(e)?zw:yw;return n.test(Qa(e))}function Mw(e,n){return e==null?void 0:e[n]}function Ga(e,n){var o=Mw(e,n);return Ow(o)?o:void 0}var Pu=Ga(Nr,"WeakMap"),lf=Object.create,$w=function(){function e(){}return function(n){if(!Ur(n))return{};if(lf)return lf(n);e.prototype=n;var o=new e;return e.prototype=void 0,o}}();function Ew(e,n,o){switch(o.length){case 0:return e.call(n);case 1:return e.call(n,o[0]);case 2:return e.call(n,o[0],o[1]);case 3:return e.call(n,o[0],o[1],o[2])}return e.apply(n,o)}function Aw(e,n){var o=-1,r=e.length;for(n||(n=Array(r));++o<r;)n[o]=e[o];return n}var Rw=800,Tw=16,Pw=Date.now;function Iw(e){var n=0,o=0;return function(){var r=Pw(),s=Tw-(r-o);if(o=r,s>0){if(++n>=Rw)return arguments[0]}else n=0;return e.apply(void 0,arguments)}}function qw(e){return function(){return e}}var Ul=function(){try{var e=Ga(Object,"defineProperty");return e({},"",{}),e}catch{}}(),Nw=Ul?function(e,n){return Ul(e,"toString",{configurable:!0,enumerable:!1,value:qw(n),writable:!0})}:Cd,Uw=Iw(Nw);function _w(e,n){for(var o=-1,r=e==null?0:e.length;++o<r&&n(e[o],o,e)!==!1;);return e}var Dw=9007199254740991,Fw=/^(?:0|[1-9]\d*)$/;function zd(e,n){var o=typeof e;return n=n??Dw,!!n&&(o=="number"||o!="symbol"&&Fw.test(e))&&e>-1&&e%1==0&&e<n}function ic(e,n,o){n=="__proto__"&&Ul?Ul(e,n,{configurable:!0,enumerable:!0,value:o,writable:!0}):e[n]=o}function Fi(e,n){return e===n||e!==e&&n!==n}var Lw=Object.prototype,Jw=Lw.hasOwnProperty;function Vh(e,n,o){var r=e[n];(!(Jw.call(e,n)&&Fi(r,o))||o===void 0&&!(n in e))&&ic(e,n,o)}function Hh(e,n,o,r){var s=!o;o||(o={});for(var a=-1,i=n.length;++a<i;){var l=n[a],c=r?r(o[l],e[l],l,o,e):void 0;c===void 0&&(c=e[l]),s?ic(o,l,c):Vh(o,l,c)}return o}var cf=Math.max;function Bw(e,n,o){return n=cf(n===void 0?e.length-1:n,0),function(){for(var r=arguments,s=-1,a=cf(r.length-n,0),i=Array(a);++s<a;)i[s]=r[n+s];s=-1;for(var l=Array(n+1);++s<n;)l[s]=r[s];return l[n]=o(i),Ew(e,this,l)}}function Ww(e,n){return Uw(Bw(e,n,Cd),e+"")}var Vw=9007199254740991;function Od(e){return typeof e=="number"&&e>-1&&e%1==0&&e<=Vw}function lc(e){return e!=null&&Od(e.length)&&!Sd(e)}function Hw(e,n,o){if(!Ur(o))return!1;var r=typeof n;return(r=="number"?lc(o)&&zd(n,o.length):r=="string"&&n in o)?Fi(o[n],e):!1}function Yh(e){return Ww(function(n,o){var r=-1,s=o.length,a=s>1?o[s-1]:void 0,i=s>2?o[2]:void 0;for(a=e.length>3&&typeof a=="function"?(s--,a):void 0,i&&Hw(o[0],o[1],i)&&(a=s<3?void 0:a,s=1),n=Object(n);++r<s;){var l=o[r];l&&e(n,l,r,a)}return n})}var Yw=Object.prototype;function Md(e){var n=e&&e.constructor,o=typeof n=="function"&&n.prototype||Yw;return e===o}function Kw(e,n){for(var o=-1,r=Array(e);++o<e;)r[o]=n(o);return r}var Qw="[object Arguments]";function uf(e){return qr(e)&&Ka(e)==Qw}var Kh=Object.prototype,Gw=Kh.hasOwnProperty,Xw=Kh.propertyIsEnumerable,_l=uf(function(){return arguments}())?uf:function(e){return qr(e)&&Gw.call(e,"callee")&&!Xw.call(e,"callee")};function Zw(){return!1}var Qh=typeof exports=="object"&&exports&&!exports.nodeType&&exports,df=Qh&&typeof module=="object"&&module&&!module.nodeType&&module,ek=df&&df.exports===Qh,vf=ek?Nr.Buffer:void 0,nk=vf?vf.isBuffer:void 0,Mi=nk||Zw,tk="[object Arguments]",ok="[object Array]",rk="[object Boolean]",ak="[object Date]",sk="[object Error]",ik="[object Function]",lk="[object Map]",ck="[object Number]",uk="[object Object]",dk="[object RegExp]",vk="[object Set]",fk="[object String]",pk="[object WeakMap]",hk="[object ArrayBuffer]",gk="[object DataView]",mk="[object Float32Array]",bk="[object Float64Array]",jk="[object Int8Array]",xk="[object Int16Array]",yk="[object Int32Array]",wk="[object Uint8Array]",kk="[object Uint8ClampedArray]",Ck="[object Uint16Array]",Sk="[object Uint32Array]",so={};so[mk]=so[bk]=so[jk]=so[xk]=so[yk]=so[wk]=so[kk]=so[Ck]=so[Sk]=!0;so[tk]=so[ok]=so[hk]=so[rk]=so[gk]=so[ak]=so[sk]=so[ik]=so[lk]=so[ck]=so[uk]=so[dk]=so[vk]=so[fk]=so[pk]=!1;function zk(e){return qr(e)&&Od(e.length)&&!!so[Ka(e)]}function $d(e){return function(n){return e(n)}}var Gh=typeof exports=="object"&&exports&&!exports.nodeType&&exports,ui=Gh&&typeof module=="object"&&module&&!module.nodeType&&module,Ok=ui&&ui.exports===Gh,Qc=Ok&&Jh.process,As=function(){try{var e=ui&&ui.require&&ui.require("util").types;return e||Qc&&Qc.binding&&Qc.binding("util")}catch{}}(),ff=As&&As.isTypedArray,Ed=ff?$d(ff):zk,Mk=Object.prototype,$k=Mk.hasOwnProperty;function Xh(e,n){var o=ar(e),r=!o&&_l(e),s=!o&&!r&&Mi(e),a=!o&&!r&&!s&&Ed(e),i=o||r||s||a,l=i?Kw(e.length,String):[],c=l.length;for(var d in e)(n||$k.call(e,d))&&!(i&&(d=="length"||s&&(d=="offset"||d=="parent")||a&&(d=="buffer"||d=="byteLength"||d=="byteOffset")||zd(d,c)))&&l.push(d);return l}function Zh(e,n){return function(o){return e(n(o))}}var Ek=Zh(Object.keys,Object),Ak=Object.prototype,Rk=Ak.hasOwnProperty;function Tk(e){if(!Md(e))return Ek(e);var n=[];for(var o in Object(e))Rk.call(e,o)&&o!="constructor"&&n.push(o);return n}function Ad(e){return lc(e)?Xh(e):Tk(e)}function Pk(e){var n=[];if(e!=null)for(var o in Object(e))n.push(o);return n}var Ik=Object.prototype,qk=Ik.hasOwnProperty;function Nk(e){if(!Ur(e))return Pk(e);var n=Md(e),o=[];for(var r in e)r=="constructor"&&(n||!qk.call(e,r))||o.push(r);return o}function Rd(e){return lc(e)?Xh(e,!0):Nk(e)}var Uk=Yh(function(e,n,o,r){Hh(n,Rd(n),e,r)}),_k=/\.|\[(?:[^[\]]*|(["'])(?:(?!\1)[^\\]|\\.)*?\1)\]/,Dk=/^\w*$/;function Td(e,n){if(ar(e))return!1;var o=typeof e;return o=="number"||o=="symbol"||o=="boolean"||e==null||kd(e)?!0:Dk.test(e)||!_k.test(e)||n!=null&&e in Object(n)}var $i=Ga(Object,"create");function Fk(){this.__data__=$i?$i(null):{},this.size=0}function Lk(e){var n=this.has(e)&&delete this.__data__[e];return this.size-=n?1:0,n}var Jk="__lodash_hash_undefined__",Bk=Object.prototype,Wk=Bk.hasOwnProperty;function Vk(e){var n=this.__data__;if($i){var o=n[e];return o===Jk?void 0:o}return Wk.call(n,e)?n[e]:void 0}var Hk=Object.prototype,Yk=Hk.hasOwnProperty;function Kk(e){var n=this.__data__;return $i?n[e]!==void 0:Yk.call(n,e)}var Qk="__lodash_hash_undefined__";function Gk(e,n){var o=this.__data__;return this.size+=this.has(e)?0:1,o[e]=$i&&n===void 0?Qk:n,this}function Ha(e){var n=-1,o=e==null?0:e.length;for(this.clear();++n<o;){var r=e[n];this.set(r[0],r[1])}}Ha.prototype.clear=Fk;Ha.prototype.delete=Lk;Ha.prototype.get=Vk;Ha.prototype.has=Kk;Ha.prototype.set=Gk;function Xk(){this.__data__=[],this.size=0}function cc(e,n){for(var o=e.length;o--;)if(Fi(e[o][0],n))return o;return-1}var Zk=Array.prototype,e2=Zk.splice;function n2(e){var n=this.__data__,o=cc(n,e);if(o<0)return!1;var r=n.length-1;return o==r?n.pop():e2.call(n,o,1),--this.size,!0}function t2(e){var n=this.__data__,o=cc(n,e);return o<0?void 0:n[o][1]}function o2(e){return cc(this.__data__,e)>-1}function r2(e,n){var o=this.__data__,r=cc(o,e);return r<0?(++this.size,o.push([e,n])):o[r][1]=n,this}function sa(e){var n=-1,o=e==null?0:e.length;for(this.clear();++n<o;){var r=e[n];this.set(r[0],r[1])}}sa.prototype.clear=Xk;sa.prototype.delete=n2;sa.prototype.get=t2;sa.prototype.has=o2;sa.prototype.set=r2;var Ei=Ga(Nr,"Map");function a2(){this.size=0,this.__data__={hash:new Ha,map:new(Ei||sa),string:new Ha}}function s2(e){var n=typeof e;return n=="string"||n=="number"||n=="symbol"||n=="boolean"?e!=="__proto__":e===null}function uc(e,n){var o=e.__data__;return s2(n)?o[typeof n=="string"?"string":"hash"]:o.map}function i2(e){var n=uc(this,e).delete(e);return this.size-=n?1:0,n}function l2(e){return uc(this,e).get(e)}function c2(e){return uc(this,e).has(e)}function u2(e,n){var o=uc(this,e),r=o.size;return o.set(e,n),this.size+=o.size==r?0:1,this}function ia(e){var n=-1,o=e==null?0:e.length;for(this.clear();++n<o;){var r=e[n];this.set(r[0],r[1])}}ia.prototype.clear=a2;ia.prototype.delete=i2;ia.prototype.get=l2;ia.prototype.has=c2;ia.prototype.set=u2;var d2="Expected a function";function Pd(e,n){if(typeof e!="function"||n!=null&&typeof n!="function")throw new TypeError(d2);var o=function(){var r=arguments,s=n?n.apply(this,r):r[0],a=o.cache;if(a.has(s))return a.get(s);var i=e.apply(this,r);return o.cache=a.set(s,i)||a,i};return o.cache=new(Pd.Cache||ia),o}Pd.Cache=ia;var v2=500;function f2(e){var n=Pd(e,function(r){return o.size===v2&&o.clear(),r}),o=n.cache;return n}var p2=/[^.[\]]+|\[(?:(-?\d+(?:\.\d+)?)|(["'])((?:(?!\2)[^\\]|\\.)*?)\2)\]|(?=(?:\.|\[\])(?:\.|\[\]|$))/g,h2=/\\(\\)?/g,g2=f2(function(e){var n=[];return e.charCodeAt(0)===46&&n.push(""),e.replace(p2,function(o,r,s,a){n.push(s?a.replace(h2,"$1"):r||o)}),n});function m2(e){return e==null?"":Wh(e)}function eg(e,n){return ar(e)?e:Td(e,n)?[e]:g2(m2(e))}function dc(e){if(typeof e=="string"||kd(e))return e;var n=e+"";return n=="0"&&1/e==-1/0?"-0":n}function ng(e,n){n=eg(n,e);for(var o=0,r=n.length;e!=null&&o<r;)e=e[dc(n[o++])];return o&&o==r?e:void 0}function b2(e,n,o){var r=e==null?void 0:ng(e,n);return r===void 0?o:r}function j2(e,n){for(var o=-1,r=n.length,s=e.length;++o<r;)e[s+o]=n[o];return e}var tg=Zh(Object.getPrototypeOf,Object),x2="[object Object]",y2=Function.prototype,w2=Object.prototype,og=y2.toString,k2=w2.hasOwnProperty,C2=og.call(Object);function S2(e){if(!qr(e)||Ka(e)!=x2)return!1;var n=tg(e);if(n===null)return!0;var o=k2.call(n,"constructor")&&n.constructor;return typeof o=="function"&&o instanceof o&&og.call(o)==C2}function z2(){this.__data__=new sa,this.size=0}function O2(e){var n=this.__data__,o=n.delete(e);return this.size=n.size,o}function M2(e){return this.__data__.get(e)}function $2(e){return this.__data__.has(e)}var E2=200;function A2(e,n){var o=this.__data__;if(o instanceof sa){var r=o.__data__;if(!Ei||r.length<E2-1)return r.push([e,n]),this.size=++o.size,this;o=this.__data__=new ia(r)}return o.set(e,n),this.size=o.size,this}function xr(e){var n=this.__data__=new sa(e);this.size=n.size}xr.prototype.clear=z2;xr.prototype.delete=O2;xr.prototype.get=M2;xr.prototype.has=$2;xr.prototype.set=A2;var rg=typeof exports=="object"&&exports&&!exports.nodeType&&exports,pf=rg&&typeof module=="object"&&module&&!module.nodeType&&module,R2=pf&&pf.exports===rg,hf=R2?Nr.Buffer:void 0,gf=hf?hf.allocUnsafe:void 0;function ag(e,n){if(n)return e.slice();var o=e.length,r=gf?gf(o):new e.constructor(o);return e.copy(r),r}function T2(e,n){for(var o=-1,r=e==null?0:e.length,s=0,a=[];++o<r;){var i=e[o];n(i,o,e)&&(a[s++]=i)}return a}function P2(){return[]}var I2=Object.prototype,q2=I2.propertyIsEnumerable,mf=Object.getOwnPropertySymbols,N2=mf?function(e){return e==null?[]:(e=Object(e),T2(mf(e),function(n){return q2.call(e,n)}))}:P2;function U2(e,n,o){var r=n(e);return ar(e)?r:j2(r,o(e))}function Iu(e){return U2(e,Ad,N2)}var qu=Ga(Nr,"DataView"),Nu=Ga(Nr,"Promise"),Uu=Ga(Nr,"Set"),bf="[object Map]",_2="[object Object]",jf="[object Promise]",xf="[object Set]",yf="[object WeakMap]",wf="[object DataView]",D2=Qa(qu),F2=Qa(Ei),L2=Qa(Nu),J2=Qa(Uu),B2=Qa(Pu),ur=Ka;(qu&&ur(new qu(new ArrayBuffer(1)))!=wf||Ei&&ur(new Ei)!=bf||Nu&&ur(Nu.resolve())!=jf||Uu&&ur(new Uu)!=xf||Pu&&ur(new Pu)!=yf)&&(ur=function(e){var n=Ka(e),o=n==_2?e.constructor:void 0,r=o?Qa(o):"";if(r)switch(r){case D2:return wf;case F2:return bf;case L2:return jf;case J2:return xf;case B2:return yf}return n});var W2=Object.prototype,V2=W2.hasOwnProperty;function H2(e){var n=e.length,o=new e.constructor(n);return n&&typeof e[0]=="string"&&V2.call(e,"index")&&(o.index=e.index,o.input=e.input),o}var Dl=Nr.Uint8Array;function Id(e){var n=new e.constructor(e.byteLength);return new Dl(n).set(new Dl(e)),n}function Y2(e,n){var o=Id(e.buffer);return new e.constructor(o,e.byteOffset,e.byteLength)}var K2=/\w*$/;function Q2(e){var n=new e.constructor(e.source,K2.exec(e));return n.lastIndex=e.lastIndex,n}var kf=Ir?Ir.prototype:void 0,Cf=kf?kf.valueOf:void 0;function G2(e){return Cf?Object(Cf.call(e)):{}}function sg(e,n){var o=n?Id(e.buffer):e.buffer;return new e.constructor(o,e.byteOffset,e.length)}var X2="[object Boolean]",Z2="[object Date]",eC="[object Map]",nC="[object Number]",tC="[object RegExp]",oC="[object Set]",rC="[object String]",aC="[object Symbol]",sC="[object ArrayBuffer]",iC="[object DataView]",lC="[object Float32Array]",cC="[object Float64Array]",uC="[object Int8Array]",dC="[object Int16Array]",vC="[object Int32Array]",fC="[object Uint8Array]",pC="[object Uint8ClampedArray]",hC="[object Uint16Array]",gC="[object Uint32Array]";function mC(e,n,o){var r=e.constructor;switch(n){case sC:return Id(e);case X2:case Z2:return new r(+e);case iC:return Y2(e);case lC:case cC:case uC:case dC:case vC:case fC:case pC:case hC:case gC:return sg(e,o);case eC:return new r;case nC:case rC:return new r(e);case tC:return Q2(e);case oC:return new r;case aC:return G2(e)}}function ig(e){return typeof e.constructor=="function"&&!Md(e)?$w(tg(e)):{}}var bC="[object Map]";function jC(e){return qr(e)&&ur(e)==bC}var Sf=As&&As.isMap,xC=Sf?$d(Sf):jC,yC="[object Set]";function wC(e){return qr(e)&&ur(e)==yC}var zf=As&&As.isSet,kC=zf?$d(zf):wC,CC=1,lg="[object Arguments]",SC="[object Array]",zC="[object Boolean]",OC="[object Date]",MC="[object Error]",cg="[object Function]",$C="[object GeneratorFunction]",EC="[object Map]",AC="[object Number]",ug="[object Object]",RC="[object RegExp]",TC="[object Set]",PC="[object String]",IC="[object Symbol]",qC="[object WeakMap]",NC="[object ArrayBuffer]",UC="[object DataView]",_C="[object Float32Array]",DC="[object Float64Array]",FC="[object Int8Array]",LC="[object Int16Array]",JC="[object Int32Array]",BC="[object Uint8Array]",WC="[object Uint8ClampedArray]",VC="[object Uint16Array]",HC="[object Uint32Array]",no={};no[lg]=no[SC]=no[NC]=no[UC]=no[zC]=no[OC]=no[_C]=no[DC]=no[FC]=no[LC]=no[JC]=no[EC]=no[AC]=no[ug]=no[RC]=no[TC]=no[PC]=no[IC]=no[BC]=no[WC]=no[VC]=no[HC]=!0;no[MC]=no[cg]=no[qC]=!1;function ul(e,n,o,r,s,a){var i,l=n&CC;if(i!==void 0)return i;if(!Ur(e))return e;var c=ar(e);if(c)i=H2(e);else{var d=ur(e),u=d==cg||d==$C;if(Mi(e))return ag(e,l);if(d==ug||d==lg||u&&!s)i=u?{}:ig(e);else{if(!no[d])return s?e:{};i=mC(e,d,l)}}a||(a=new xr);var h=a.get(e);if(h)return h;a.set(e,i),kC(e)?e.forEach(function(g){i.add(ul(g,n,o,g,e,a))}):xC(e)&&e.forEach(function(g,b){i.set(b,ul(g,n,o,b,e,a))});var p=Iu,m=c?void 0:p(e);return _w(m||e,function(g,b){m&&(b=g,g=e[b]),Vh(i,b,ul(g,n,o,b,e,a))}),i}var YC=1,KC=4;function QC(e){return ul(e,YC|KC)}var GC="__lodash_hash_undefined__";function XC(e){return this.__data__.set(e,GC),this}function ZC(e){return this.__data__.has(e)}function Fl(e){var n=-1,o=e==null?0:e.length;for(this.__data__=new ia;++n<o;)this.add(e[n])}Fl.prototype.add=Fl.prototype.push=XC;Fl.prototype.has=ZC;function eS(e,n){for(var o=-1,r=e==null?0:e.length;++o<r;)if(n(e[o],o,e))return!0;return!1}function nS(e,n){return e.has(n)}var tS=1,oS=2;function dg(e,n,o,r,s,a){var i=o&tS,l=e.length,c=n.length;if(l!=c&&!(i&&c>l))return!1;var d=a.get(e),u=a.get(n);if(d&&u)return d==n&&u==e;var h=-1,p=!0,m=o&oS?new Fl:void 0;for(a.set(e,n),a.set(n,e);++h<l;){var g=e[h],b=n[h];if(r)var j=i?r(b,g,h,n,e,a):r(g,b,h,e,n,a);if(j!==void 0){if(j)continue;p=!1;break}if(m){if(!eS(n,function(x,k){if(!nS(m,k)&&(g===x||s(g,x,o,r,a)))return m.push(k)})){p=!1;break}}else if(!(g===b||s(g,b,o,r,a))){p=!1;break}}return a.delete(e),a.delete(n),p}function rS(e){var n=-1,o=Array(e.size);return e.forEach(function(r,s){o[++n]=[s,r]}),o}function aS(e){var n=-1,o=Array(e.size);return e.forEach(function(r){o[++n]=r}),o}var sS=1,iS=2,lS="[object Boolean]",cS="[object Date]",uS="[object Error]",dS="[object Map]",vS="[object Number]",fS="[object RegExp]",pS="[object Set]",hS="[object String]",gS="[object Symbol]",mS="[object ArrayBuffer]",bS="[object DataView]",Of=Ir?Ir.prototype:void 0,Gc=Of?Of.valueOf:void 0;function jS(e,n,o,r,s,a,i){switch(o){case bS:if(e.byteLength!=n.byteLength||e.byteOffset!=n.byteOffset)return!1;e=e.buffer,n=n.buffer;case mS:return!(e.byteLength!=n.byteLength||!a(new Dl(e),new Dl(n)));case lS:case cS:case vS:return Fi(+e,+n);case uS:return e.name==n.name&&e.message==n.message;case fS:case hS:return e==n+"";case dS:var l=rS;case pS:var c=r&sS;if(l||(l=aS),e.size!=n.size&&!c)return!1;var d=i.get(e);if(d)return d==n;r|=iS,i.set(e,n);var u=dg(l(e),l(n),r,s,a,i);return i.delete(e),u;case gS:if(Gc)return Gc.call(e)==Gc.call(n)}return!1}var xS=1,yS=Object.prototype,wS=yS.hasOwnProperty;function kS(e,n,o,r,s,a){var i=o&xS,l=Iu(e),c=l.length,d=Iu(n),u=d.length;if(c!=u&&!i)return!1;for(var h=c;h--;){var p=l[h];if(!(i?p in n:wS.call(n,p)))return!1}var m=a.get(e),g=a.get(n);if(m&&g)return m==n&&g==e;var b=!0;a.set(e,n),a.set(n,e);for(var j=i;++h<c;){p=l[h];var x=e[p],k=n[p];if(r)var $=i?r(k,x,p,n,e,a):r(x,k,p,e,n,a);if(!($===void 0?x===k||s(x,k,o,r,a):$)){b=!1;break}j||(j=p=="constructor")}if(b&&!j){var S=e.constructor,y=n.constructor;S!=y&&"constructor"in e&&"constructor"in n&&!(typeof S=="function"&&S instanceof S&&typeof y=="function"&&y instanceof y)&&(b=!1)}return a.delete(e),a.delete(n),b}var CS=1,Mf="[object Arguments]",$f="[object Array]",rl="[object Object]",SS=Object.prototype,Ef=SS.hasOwnProperty;function zS(e,n,o,r,s,a){var i=ar(e),l=ar(n),c=i?$f:ur(e),d=l?$f:ur(n);c=c==Mf?rl:c,d=d==Mf?rl:d;var u=c==rl,h=d==rl,p=c==d;if(p&&Mi(e)){if(!Mi(n))return!1;i=!0,u=!1}if(p&&!u)return a||(a=new xr),i||Ed(e)?dg(e,n,o,r,s,a):jS(e,n,c,o,r,s,a);if(!(o&CS)){var m=u&&Ef.call(e,"__wrapped__"),g=h&&Ef.call(n,"__wrapped__");if(m||g){var b=m?e.value():e,j=g?n.value():n;return a||(a=new xr),s(b,j,o,r,a)}}return p?(a||(a=new xr),kS(e,n,o,r,s,a)):!1}function qd(e,n,o,r,s){return e===n?!0:e==null||n==null||!qr(e)&&!qr(n)?e!==e&&n!==n:zS(e,n,o,r,qd,s)}var OS=1,MS=2;function $S(e,n,o,r){var s=o.length,a=s;if(e==null)return!a;for(e=Object(e);s--;){var i=o[s];if(i[2]?i[1]!==e[i[0]]:!(i[0]in e))return!1}for(;++s<a;){i=o[s];var l=i[0],c=e[l],d=i[1];if(i[2]){if(c===void 0&&!(l in e))return!1}else{var u=new xr,h;if(!(h===void 0?qd(d,c,OS|MS,r,u):h))return!1}}return!0}function vg(e){return e===e&&!Ur(e)}function ES(e){for(var n=Ad(e),o=n.length;o--;){var r=n[o],s=e[r];n[o]=[r,s,vg(s)]}return n}function fg(e,n){return function(o){return o==null?!1:o[e]===n&&(n!==void 0||e in Object(o))}}function AS(e){var n=ES(e);return n.length==1&&n[0][2]?fg(n[0][0],n[0][1]):function(o){return o===e||$S(o,e,n)}}function RS(e,n){return e!=null&&n in Object(e)}function TS(e,n,o){n=eg(n,e);for(var r=-1,s=n.length,a=!1;++r<s;){var i=dc(n[r]);if(!(a=e!=null&&o(e,i)))break;e=e[i]}return a||++r!=s?a:(s=e==null?0:e.length,!!s&&Od(s)&&zd(i,s)&&(ar(e)||_l(e)))}function PS(e,n){return e!=null&&TS(e,n,RS)}var IS=1,qS=2;function NS(e,n){return Td(e)&&vg(n)?fg(dc(e),n):function(o){var r=b2(o,e);return r===void 0&&r===n?PS(o,e):qd(n,r,IS|qS)}}function US(e){return function(n){return n==null?void 0:n[e]}}function _S(e){return function(n){return ng(n,e)}}function DS(e){return Td(e)?US(dc(e)):_S(e)}function FS(e){return typeof e=="function"?e:e==null?Cd:typeof e=="object"?ar(e)?NS(e[0],e[1]):AS(e):DS(e)}function LS(e){return function(n,o,r){for(var s=-1,a=Object(n),i=r(n),l=i.length;l--;){var c=i[++s];if(o(a[c],c,a)===!1)break}return n}}var pg=LS();function JS(e,n){return e&&pg(e,n,Ad)}function _u(e,n,o){(o!==void 0&&!Fi(e[n],o)||o===void 0&&!(n in e))&&ic(e,n,o)}function BS(e){return qr(e)&&lc(e)}function Du(e,n){if(!(n==="constructor"&&typeof e[n]=="function")&&n!="__proto__")return e[n]}function WS(e){return Hh(e,Rd(e))}function VS(e,n,o,r,s,a,i){var l=Du(e,o),c=Du(n,o),d=i.get(c);if(d){_u(e,o,d);return}var u=a?a(l,c,o+"",e,n,i):void 0,h=u===void 0;if(h){var p=ar(c),m=!p&&Mi(c),g=!p&&!m&&Ed(c);u=c,p||m||g?ar(l)?u=l:BS(l)?u=Aw(l):m?(h=!1,u=ag(c,!0)):g?(h=!1,u=sg(c,!0)):u=[]:S2(c)||_l(c)?(u=l,_l(l)?u=WS(l):(!Ur(l)||Sd(l))&&(u=ig(c))):h=!1}h&&(i.set(c,u),s(u,c,r,a,i),i.delete(c)),_u(e,o,u)}function hg(e,n,o,r,s){e!==n&&pg(n,function(a,i){if(s||(s=new xr),Ur(a))VS(e,n,i,o,hg,r,s);else{var l=r?r(Du(e,i),a,i+"",e,n,s):void 0;l===void 0&&(l=a),_u(e,i,l)}},Rd)}var HS=Yh(function(e,n,o,r){hg(e,n,o,r)});function YS(e,n){var o={};return n=FS(n),JS(e,function(r,s,a){ic(o,n(r,s,a),r)}),o}const KS=new RegExp("([\\p{Ll}\\d])(\\p{Lu})","gu"),QS=new RegExp("(\\p{Lu})([\\p{Lu}][\\p{Ll}])","gu"),GS=new RegExp("(\\d)\\p{Ll}|(\\p{L})\\d","u"),XS=/[^\p{L}\d]+/giu,Af="$1\0$2",Rf="";function gg(e){let n=e.trim();n=n.replace(KS,Af).replace(QS,Af),n=n.replace(XS,"\0");let o=0,r=n.length;for(;n.charAt(o)==="\0";)o++;if(o===r)return[];for(;n.charAt(r-1)==="\0";)r--;return n.slice(o,r).split(/\0/g)}function ZS(e){const n=gg(e);for(let o=0;o<n.length;o++){const r=n[o],s=GS.exec(r);if(s){const a=s.index+(s[1]??s[2]).length;n.splice(o,1,r.slice(0,a),r.slice(a))}}return n}function ez(e,n){const[o,r,s]=rz(e,n),a=nz(void 0),i=tz(void 0),l=oz(a,i);return o+r.map((c,d)=>d===0?a(c):l(c,d)).join("")+s}function nz(e){return n=>n.toLocaleLowerCase(e)}function tz(e){return n=>n.toLocaleUpperCase(e)}function oz(e,n){return(o,r)=>{const s=o[0];return(r>0&&s>="0"&&s<="9"?"_"+s:n(s))+e(o.slice(1))}}function rz(e,n={}){const o=n.split??(n.separateNumbers?ZS:gg),r=n.prefixCharacters??Rf,s=n.suffixCharacters??Rf;let a=0,i=e.length;for(;a<e.length;){const l=e.charAt(a);if(!r.includes(l))break;a++}for(;i>a;){const l=i-1,c=e.charAt(l);if(!s.includes(c))break;i=l}return[e.slice(0,a),o(e.slice(a,i)),e.slice(i)]}const di=e=>/.+-.+/.test(e)?ez(e):e,az=e=>Object.prototype.toString.call(e).slice(8,-1)==="Object";function sz(e,n){const o=Object.create(null),r=e.split(",");for(let s=0;s<r.length;s++)o[r[s]]=!0;return s=>!!o[s]}const iz=sz("String,Number,Boolean,Function,Symbol,BigInt");function lz(e){const n=e==null?void 0:e.toString().match(/^\s*function (\w+)/);return n?n[1]:e===null?"null":""}function cz(e,n){let o;const r=lz(n);if(iz(r)){const s=typeof e;o=s===r.toLowerCase(),!o&&s==="object"&&(o=e instanceof n)}else r==="Object"?o=Ur(e):r==="Array"?o=Array.isArray(e):r==="null"?o=e===null:o=e instanceof n;return{valid:o,expectedType:r}}function uz({prop:e,type:n,validator:o}){if(![void 0,null].includes(e)&&n){let r=!1;const s=Array.isArray(n)?n:[n],a=[];for(let i=0;i<s.length&&!r;i++){const{valid:l,expectedType:c}=cz(e,s[i]);a.push(c||""),r=l}if(!r)throw new TypeError(`Invalid prop: type check failed, expecting [${a.join(", ")}], receiving: ${e}`)}if(o&&!o(e))throw new Error(`Invalid prop: validator check failed, receiving: ${e}`)}function dz(e,{mergeObject:n,mergeObjectCustomizer:o,mergeFunction:r}){const s=[];for(let i=e.length-1;i>=0;i--)s.push(e[i]);const a=o||(r?(i,l)=>typeof i=="function"&&typeof l=="function"?r==null?void 0:r(l,i):void 0:void 0);return n==="deep"?HS(...s,a):Uk(...s,a)}function vz(e,{mergeFunction:n}){return e.reduce(n,()=>{})}function va(e,n={}){const{type:o,default:r,defaultIsDynamic:s=!1,required:a=!1,validator:i,camelizeObjectKeys:l=!1,mergeObjectApplyOnlyToDefault:c=!1,mergeFunctionApplyOnlyToDefault:d=!0}=n;let{mergeObject:u="deep",mergeObjectCustomizer:h,mergeFunction:p=!1}=n;const m=[];let g,b=!1,j=!1;const x=k=>{if(k!==void 0){uz({type:o,prop:k,validator:i});const $=az(k),S=typeof k=="function";return b=$,j=S,$?(k=QC(k),l?YS(k,(y,R)=>di(R)):k):k}};for(const k of e)m.push(x(k));if(!s)m.push(x(r));else if(typeof r!="function")throw new TypeError(`Invalid option: options.default should be Function when options.defaultIsDynamic enabled, receiving: ${r}`);b||(u=!1,j||(p=!1));for(let k=0;k<m.length;k++){const $=m[k];if($!==void 0){k===m.length-1?g=$:u?g=dz(c?[$,r]:m,{mergeObject:u,mergeObjectCustomizer:h,mergeFunction:p}):p?g=vz(d?[$,r]:m,{mergeFunction:p}):g=$;break}}if(a&&[void 0,null].includes(g))throw new Error("Missing required prop");return s?va(e,{...n,default:r(g),defaultIsDynamic:!1}):g}function Tf(e){const n=Array.from(di(e));return n[0]=n[0].toUpperCase(),n.unshift("o","n"),n.join("")}function fz(e,{props:n=[],camelizePropNames:o=!1}={}){const r={props:{},attrs:{},listeners:{},hooks:{},slots:{}};let s;if(Array.isArray(n))s=o?n.map(a=>di(a)):n;else if(o){s=[];for(const a in n)s.push(di(a))}else s=Object.keys(n);for(const a in e)if(a.startsWith("@")){const i=a.substring(1);if(i.startsWith("vue:"))r.hooks[Tf(i.replace("vue:","vnode-"))]=e[a];else{if(i.startsWith("vnode"))throw new Error("@vnode-* hooks are no longer supported. Use the vue: prefix instead. For example, @vnode-mounted should be changed to @vue:mounted. @vnode-* hooks support has been removed in Vue 3.4.");r.listeners[Tf(i)]=e[a]}}else if(a.startsWith("#")){const i=a.substring(1);i&&(r.slots[i]=e[a])}else{const i=o?di(a):a;s.includes(i)?r.props[i]=e[a]:r.attrs[a]=e[a]}return r}const Pf="JsonEditorVue",dl=["mainMenuBar","navigationBar","statusBar","askToFormat","readOnly","escapeControlCharacters","escapeUnicodeCharacters","flattenColumns"],ca={},If={},vl="modelValue",qf="update:modelValue",Nf={[vl]:{},mode:{type:String},debounce:{type:Number},stringified:{type:Boolean,default:void 0},...Object.fromEntries(dl.map(e=>[e,{type:Boolean,default:void 0}]))},gz=qm({name:Pf,install(e,n){const o=fz(n||{},{props:Nf});Object.assign(ca,o.props),Object.assign(If,o.attrs),e.component(Pf,this)},props:Nf,emits:{[qf](e){return!0},"update:mode":function(e){return!0}},setup(e,{attrs:n,emit:o,expose:r}){var s;const a=(s=Nm())==null?void 0:s.proxy,i=bc(),l=bc(!1),c=bc();Um(()=>{var x;c.value=va([e.mode,ca.mode],{type:String}),(x=i.value)==null||x.updateProps({mode:c.value||yo.tree})});const d=x=>{o("update:mode",x)};ca.mode!==void 0&&e.mode===void 0&&d(ca.mode);const u=Bd(()=>va([e.debounce,ca.debounce,300],{type:Number})),h=Bd(()=>va([e.stringified,ca.stringified,!0],{type:Boolean}));let p=cl;const m=x=>{l.value=!0,!h.value&&x.text&&(i.value&&!i.value.validate()&&(x.json=p(x.text)),x.text=void 0),o(qf,x.text===void 0?x.json:x.text)},g=nw(m,u.value),b=x=>{c.value==="text"?g(x):m(x)},j=(x,k)=>(...$)=>{x(...$),k(...$)};return r==null||r({jsonEditor:i}),_m(()=>{var x;(x=i.value)==null||x.destroy()}),Dm(()=>{const x=va([e[vl],ca[vl]]),k=Object.fromEntries(Array.from(dl,S=>[S,va([e[S],ca[S]])]).filter(([,S])=>S!==void 0)),$=va([k,n,If],{camelizeObjectKeys:!0,defaultIsDynamic:!0,default:S=>{var y;return p=((y=S.parser)==null?void 0:y.parse)||cl,{onChange:b,onChangeMode:d,mode:c.value,parser:{parse:$y,stringify:JSON.stringify},...x!==void 0&&{content:{[typeof x=="string"&&c.value==="text"&&h.value?"text":"json"]:x}}}},mergeFunction:j,mergeObject:"shallow",type:Object});i.value=ky({target:a==null?void 0:a.$refs.jsonEditorRef,props:$}),jc(()=>e[vl],S=>{if(l.value){l.value=!1;return}i.value&&i.value.set([void 0,""].includes(S)?{text:""}:{[typeof S=="string"&&c.value==="text"&&h.value?"text":"json"]:S})},{deep:!0}),jc(()=>Array.from(dl,S=>e[S]),S=>{var y;(y=i.value)==null||y.updateProps(Object.fromEntries(Array.from(S,(R,N)=>[dl[N],R]).filter(([,R])=>R!==void 0)))}),jc(()=>n,S=>{var y,R;const N={};(S.onChange||S["on-change"])&&(N.onChange=b),(S.onChangeMode||S["on-change-mode"])&&(N.onChangeMode=d),p=((y=S.parser)==null?void 0:y.parse)||cl,(R=i.value)==null||R.updateProps(Object.getOwnPropertyNames(N).length>0?va([S,N],{camelizeObjectKeys:!0,mergeFunction:j,mergeObject:"shallow",type:Object}):S)},{deep:!0}),r||(r=S=>{for(const y in S)a[y]=Fm(S[y])},r({jsonEditor:i}))}),()=>Lm("div",{ref:"jsonEditorRef"})}});export{Wm as _,gz as i};
