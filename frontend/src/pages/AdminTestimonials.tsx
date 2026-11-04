import { useCallback,useEffect,useState } from "react";
import { apiUrl } from "../api/api";
import "./TestimonialCards.css";
type Item={id:number;displayName:string;content:string;photos:string[];status:string;publicationConsent:boolean;displayOnPublic:boolean;featuredOnHome:boolean;createdAt:string;ownerEmail:string|null};
const token=()=>localStorage.getItem("adminToken")||"";
async function request(path:string,init:RequestInit={}){const r=await fetch(apiUrl(path),{...init,headers:{Authorization:`Bearer ${token()}`,"Content-Type":"application/json",...(init.headers||{})}});if(!r.ok)throw new Error((await r.text())||`Request failed (${r.status})`);return r.status===204?null:r.json();}
export default function AdminTestimonials(){
 const [items,setItems]=useState<Item[]>([]),[error,setError]=useState(""),[busy,setBusy]=useState(false);
 const load=useCallback(async()=>{try{setError("");setItems(await request("/api/admin/testimonials"));}catch(e){setError(e instanceof Error?e.message:"Could not load testimonials.");}},[]);
 useEffect(()=>{void load();},[load]);
 async function action(id:number,verb:string){setBusy(true);setError("");try{await request(`/api/admin/testimonials/${id}/${verb}`,{method:"POST"});await load();}catch(e){setError(e instanceof Error?e.message:"Action failed.");}finally{setBusy(false);}}
 async function display(item:Item,field:"displayOnPublic"|"featuredOnHome",value:boolean){setBusy(true);setError("");try{await request(`/api/admin/testimonials/${item.id}/display`,{method:"PUT",body:JSON.stringify({displayOnPublic:field==="displayOnPublic"?value:item.displayOnPublic,featuredOnHome:field==="featuredOnHome"?value:item.displayOnPublic&&value?true:false})});await load();}catch(e){setError(e instanceof Error?e.message:"Could not update display settings.");}finally{setBusy(false);}}
 const pending=items.filter(x=>x.status==="PENDING");
 return <div className="admin-testimonials"><div className="dashboard-section-heading"><div><h2>Testimonials</h2><p>Review submissions and choose what appears publicly. Homepage selections are limited to two.</p></div><button onClick={()=>void load()}>Refresh</button></div>
 {error&&<p role="alert" className="dashboard-alert dashboard-alert-error">{error}</p>}
 {pending.length===0&&<p className="testimonial-muted">No pending testimonials.</p>}
 {items.map(item=><article key={item.id} className="dashboard-detail" style={{marginBottom:18}}>
  <div className="dashboard-detail-header"><div><p className="dashboard-eyebrow">{item.status} · {new Date(item.createdAt).toLocaleString()}</p><h3>{item.displayName}</h3><small>{item.ownerEmail}</small></div>
   {item.status==="PENDING"&&<div className="testimonial-actions"><button disabled={busy} className="testimonial-primary" onClick={()=>void action(item.id,"approve")}>Approve</button><button disabled={busy} onClick={()=>void action(item.id,"reject")}>Reject</button></div>}</div>
  <p style={{whiteSpace:"pre-wrap"}}>{item.content}</p>
  {item.photos?.length>0&&<div className="testimonial-grid">{item.photos.map((p,i)=><img key={i} src={p} alt={`Customer photo ${i+1}`} style={{width:"100%",height:110,objectFit:"cover",borderRadius:10}}/>)}</div>}
  {item.status==="APPROVED"&&<div className="testimonial-actions" style={{marginTop:14}}>
   <label><input type="checkbox" checked={item.displayOnPublic} disabled={busy} onChange={e=>void display(item,"displayOnPublic",e.target.checked)}/> Show on public Testimonials page</label>
   <label><input type="checkbox" checked={item.featuredOnHome} disabled={busy||!item.displayOnPublic} onChange={e=>void display(item,"featuredOnHome",e.target.checked)}/> Feature on homepage (max 2)</label>
   <button disabled={busy} onClick={()=>{if(window.confirm("Remove this testimonial from public display?"))void action(item.id,"remove");}}>Remove from public display</button>
  </div>}
 </article>)}
 </div>;
}
