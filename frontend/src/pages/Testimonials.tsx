import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { apiUrl } from "../api/api";
import { usePublicFeatures } from "../context/PublicFeatureContext";
import "./TestimonialCards.css";

type Testimonial = { id:number; displayName:string; content:string; photos:string[] };

export default function Testimonials() {
  const [items,setItems]=useState<Testimonial[]>([]);
  const [selected,setSelected]=useState<Testimonial|null>(null);
  const [photoIndex,setPhotoIndex]=useState(0);
  const [params]=useSearchParams();
  const {isEnabled}=usePublicFeatures();

  useEffect(()=>{ if(!isEnabled("TESTIMONIALS")) return;
    fetch(apiUrl("/api/testimonials/public")).then(r=>r.ok?r.json():[]).then((d:Testimonial[])=>setItems(d)).catch(()=>setItems([]));
  },[isEnabled]);

  useEffect(()=>{ const id=Number(params.get("id")); if(id&&items.length){const found=items.find(x=>x.id===id);if(found)open(found);} },[items,params]);

  function open(item:Testimonial){setSelected(item);setPhotoIndex(0);}
  if(!isEnabled("TESTIMONIALS")) return <main className="testimonial-section"><div className="testimonial-wrap">This section is currently unavailable.</div></main>;

  return <main className="testimonial-section"><div className="testimonial-wrap">
    <header className="testimonial-heading"><p className="section-label">SHARED EXPERIENCES</p><h1>Stories from our community</h1><p>Every journey is different. These words reflect the experiences shared by people in our community.</p></header>
    {items.length===0?<p className="testimonial-muted">There are no published testimonials yet.</p>:
      <div className="testimonial-grid">{items.map(item=><article className="testimonial-card" key={item.id}>
        {item.photos?.[0]&&<img className="testimonial-card-photo" src={item.photos[0]} alt="" />}
        <div className="testimonial-card-body"><h3>{item.displayName}</h3><p className="testimonial-preview">{item.content}</p><button className="testimonial-open" onClick={()=>open(item)}>Read full testimonial →</button></div>
      </article>)}</div>}
  </div>
  {selected&&<div className="testimonial-modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)setSelected(null);}}>
    <section className="testimonial-modal" role="dialog" aria-modal="true" aria-labelledby="testimonial-modal-title">
      <button className="testimonial-modal-close" aria-label="Close testimonial" onClick={()=>setSelected(null)}>×</button>
      <h2 id="testimonial-modal-title">{selected.displayName}</h2><p>{selected.content}</p>
      {selected.photos?.length>0&&<><img className="testimonial-photos" src={selected.photos[photoIndex]} alt={`Photo ${photoIndex+1} of ${selected.photos.length}`} />
        {selected.photos.length>1&&<div className="testimonial-photo-nav"><button onClick={()=>setPhotoIndex(i=>(i-1+selected.photos.length)%selected.photos.length)}>Previous</button><span>{photoIndex+1} / {selected.photos.length}</span><button onClick={()=>setPhotoIndex(i=>(i+1)%selected.photos.length)}>Next</button></div>}</>}
    </section>
  </div>}
  </main>;
}
