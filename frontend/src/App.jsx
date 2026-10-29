import { useEffect, useState } from "react";
import { isApiUrlSecure } from "./security.js";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? "http://localhost:8080" : "")).replace(/\/+$/, "");

// Bound network waits so a sleeping/unreachable API never leaves the UI spinning forever.
async function apiFetch(url, options = {}) {
  if (!isApiUrlSecure(API_BASE_URL, import.meta.env.PROD)) {
    throw new Error("Production API URL must use HTTPS. Configure VITE_API_BASE_URL with an https:// origin.");
  }
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 12000);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new Error("The server took too long to respond. Please try again.");
    }
    if (error instanceof TypeError) {
      throw new Error("Cannot reach the CAMPUSOS server. Check your connection and try again.");
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}


const navItems = [["Overview","⌂"],["Tickets","✓"],["Report Issue","+"],["Resources","▣"],["Notifications","◌"],["Emergency","!"]];

function Login({ onLogin }) {
  const [email,setEmail]=useState(""); const [password,setPassword]=useState(""); const [loading,setLoading]=useState(false); const [error,setError]=useState("");
  async function submit(event){event.preventDefault();setError("");setLoading(true);try{
    const response=await apiFetch(`${API_BASE_URL}/api/auth/login`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email,password})});
    const data=await response.json(); if(!response.ok||!data.success) throw new Error(data.message||"Login failed"); onLogin(data.user,data.token);
  }catch(err){setError(err.message||"Unable to connect to CAMPUSOS server");}finally{setLoading(false);}}
  return <div className="login-shell"><div className="login-card"><div className="brand login-brand"><div className="brand-mark">C</div><div><strong>CAMPUSOS</strong><span>Campus Operations</span></div></div><p className="eyebrow">SECURE ACCESS</p><h1>Welcome back.</h1><p className="login-copy">Sign in to access your campus operations workspace.</p><form onSubmit={submit} className="login-form"><label>Email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@campusos.com" required/></label><label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="••••••••" required/></label>{error&&<div className="error-message">{error}</div>}<button className="primary-button login-button" disabled={loading}>{loading?"Signing in...":"Sign in"}</button></form><div className="login-footer">CAMPUSOS • Campus Operations & Emergency Management</div></div></div>;
}

function ReportIssue({user,token,onCreated}) {
  const [form,setForm]=useState({title:"",description:"",category:"Electrical",location:""}); const [loading,setLoading]=useState(false); const [message,setMessage]=useState(""); const [error,setError]=useState("");
  const change=e=>setForm({...form,[e.target.name]:e.target.value});
  async function submit(e){e.preventDefault();setLoading(true);setMessage("");setError("");try{
    const r=await apiFetch(`${API_BASE_URL}/api/issues`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify(form)});
    const d=await r.json(); if(!r.ok||!d.success) throw new Error(d.message||"Unable to report issue");
    setMessage(`Ticket #${d.ticketId} created • Priority: ${d.priority}`); setForm({title:"",description:"",category:"Electrical",location:""}); onCreated();
  }catch(err){setError(err.message);}finally{setLoading(false);}}
  return <section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">CAMPUS OPERATIONS</p><h3>Report an issue</h3></div></div><form className="issue-form" onSubmit={submit}><label>Issue title<input name="title" value={form.title} onChange={change} placeholder="e.g. Projector not working" required/></label><div className="form-row"><label>Category<select name="category" value={form.category} onChange={change}><option>Electrical</option><option>Network</option><option>Plumbing</option><option>IT</option><option>Security</option><option>Other</option></select></label><label>Location<input name="location" value={form.location} onChange={change} placeholder="Block / Room"/></label></div><label>Description<textarea name="description" value={form.description} onChange={change} placeholder="Describe the issue..." rows="5" required/></label>{message&&<div className="success-message">{message}</div>}{error&&<div className="error-message">{error}</div>}<button className="primary-button" disabled={loading}>{loading?"Submitting...":"Submit issue"}</button></form></section>;
}


function StaffQueue({token,user}) {
  const [tickets,setTickets]=useState([]); const [staff,setStaff]=useState([]); const [selected,setSelected]=useState({}); const [loading,setLoading]=useState(true); const [error,setError]=useState(""); const [busy,setBusy]=useState(0);
  const [page,setPage]=useState(1); const [pageSize]=useState(50); const [totalTickets,setTotalTickets]=useState(0); const [totalPages,setTotalPages]=useState(1);
  async function load(requestedPage=page){setLoading(true);setError("");try{const headers={"Authorization":`Bearer ${token}`};const r=await apiFetch(`${API_BASE_URL}/api/staff/tickets?page=${requestedPage}&pageSize=${pageSize}`,{headers});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load staff tickets");setTickets(d.tickets||[]);setPage(d.page||requestedPage);setTotalTickets(d.totalTickets||0);setTotalPages(d.totalPages||1);if(user.role==="ADMIN"){const sr=await apiFetch(`${API_BASE_URL}/api/admin/staff`,{headers});const sd=await sr.json();if(!sr.ok||!sd.success)throw new Error(sd.message||"Unable to load assignable staff");setStaff(sd.staff||[]);}}catch(e){setError(e.message||"Unable to load tickets");}finally{setLoading(false);}}
  useEffect(()=>{load();},[token]);
  async function update(id,status){setBusy(id);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/staff/tickets/${id}/status`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({status})});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Status update failed");await load();}catch(e){setError(e.message||"Status update failed");}finally{setBusy(0);}}
  async function assign(id){const staffId=Number(selected[id]||0);if(!staffId){setError("Choose a staff member first.");return;}setBusy(id);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/tickets/${id}/assign`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({staffId})});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Assignment failed");await load();}catch(e){setError(e.message||"Assignment failed");}finally{setBusy(0);}}
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">WORK QUEUE</p><h3>{user.role==="ADMIN"?"All tickets":"Assigned tickets"}</h3></div><button className="text-button" onClick={load}>Refresh</button></div>{error&&<div className="error-message">{error}</div>}{loading?<div className="empty-state">Loading work queue...</div>:tickets.length===0?<div className="empty-state"><strong>No tickets in the queue</strong><span>New assignments will appear here.</span></div>:<div className="ticket-list">{tickets.map(t=><div className="ticket-row" key={t.id}><div><strong>#{t.id} · {t.title}</strong><span>{t.category} · {t.location||"Location not specified"} · {t.priority}</span><span>Reported by user #{t.reporterId}</span></div><div className="ticket-meta"><span className="badge neutral">{t.status}</span>{user.role==="ADMIN"&&t.status==="OPEN"&&<div className="assign-controls"><select aria-label={`Assign ticket ${t.id}`} value={selected[t.id]||""} onChange={e=>setSelected(old=>({...old,[t.id]:e.target.value}))}><option value="">Choose staff</option>{staff.map(s=><option key={s.id} value={s.id}>{s.name} · {s.role}{s.specialization?` · ${s.specialization}`:""}</option>)}</select><button className="primary-button compact-button" disabled={busy===t.id} onClick={()=>assign(t.id)}>Assign</button></div>}{t.status==="ASSIGNED"&&<button className="primary-button compact-button" disabled={busy===t.id} onClick={()=>update(t.id,"IN_PROGRESS")}>Start work</button>}{t.status==="IN_PROGRESS"&&<button className="primary-button compact-button" disabled={busy===t.id} onClick={()=>update(t.id,"RESOLVED")}>Resolve</button>}{t.status==="RESOLVED"&&<button className="primary-button compact-button" disabled={busy===t.id} onClick={()=>update(t.id,"CLOSED")}>Close</button>}</div></div>)}</div>}<div className="ticket-pagination"><span>Showing {totalTickets===0?0:(page-1)*pageSize+1}–{Math.min(page*pageSize,totalTickets)} of {totalTickets}</span><div><button className="text-button" disabled={loading||page<=1} onClick={()=>load(page-1)}>Previous</button><span>Page {page} of {totalPages}</span><button className="text-button" disabled={loading||page>=totalPages} onClick={()=>load(page+1)}>Next</button></div></div></section>;
}

function MyTicketsPage({tickets,loading,error,onRefresh,token,page,totalTickets,onPageChange}) {
  const [historyFor,setHistoryFor]=useState(0); const [history,setHistory]=useState([]); const [historyLoading,setHistoryLoading]=useState(false); const [historyError,setHistoryError]=useState("");
  async function toggleHistory(id){if(historyFor===id){setHistoryFor(0);setHistory([]);return;}setHistoryFor(id);setHistory([]);setHistoryError("");setHistoryLoading(true);try{const r=await apiFetch(`${API_BASE_URL}/api/tickets/${id}/history`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load ticket history");setHistory(d.history||[]);}catch(e){setHistoryError(e.message||"Unable to load history");}finally{setHistoryLoading(false);}}
  const pageCount=Math.max(1,Math.ceil(totalTickets/20));
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">MY REQUESTS</p><h3>All my tickets</h3></div><button className="text-button" onClick={onRefresh}>Refresh</button></div>{error&&<div className="error-message">{error}</div>}{loading?<div className="empty-state">Loading tickets...</div>:tickets.length===0?<div className="empty-state"><strong>No tickets yet</strong><span>Report an issue to start tracking it here.</span></div>:<><div className="ticket-list">{tickets.map(t=><div className="ticket-entry" key={t.id}><div className="ticket-row"><div><strong>#{t.id} · {t.title}</strong><span>{t.category} · {t.location||"Location not specified"}</span><span>Created {new Date(t.createdAt).toLocaleString()}</span></div><div className="ticket-meta"><span className={`priority ${String(t.priority||"low").toLowerCase()}`}>{t.priority}</span><span className="badge neutral">{t.status}</span><button className="text-button" onClick={()=>toggleHistory(t.id)}>{historyFor===t.id?"Hide timeline":"Timeline"}</button></div></div>{historyFor===t.id&&<div className="history-panel">{historyLoading?<span>Loading timeline...</span>:historyError?<span className="history-error">{historyError}</span>:history.length===0?<span>No status changes recorded yet.</span>:history.map((item,index)=><div className="history-item" key={index}><strong>{item.fromStatus} → {item.toStatus}</strong><span>{item.changedBy} · {new Date(item.createdAt).toLocaleString()}</span></div>)}</div>}</div>)}</div><div className="ticket-pagination"><span>Showing {(page-1)*20+1}–{Math.min(page*20,totalTickets)} of {totalTickets}</span><div><button className="text-button" disabled={page<=1||loading} onClick={()=>onPageChange(page-1)}>Previous</button><span>Page {page} of {pageCount}</span><button className="text-button" disabled={page>=pageCount||loading} onClick={()=>onPageChange(page+1)}>Next</button></div></div></>}</section>;
}

function NotificationsPage({token}) {
  const [items,setItems]=useState([]); const [loading,setLoading]=useState(true); const [error,setError]=useState("");
  async function load(){setLoading(true);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/notifications/me`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load notifications");setItems(d.notifications||[]);}catch(e){setError(e.message||"Unable to load notifications");}finally{setLoading(false);}}
  useEffect(()=>{load();},[token]);
  async function markRead(id){try{const r=await apiFetch(`${API_BASE_URL}/api/notifications/${id}/read`,{method:"POST",headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to update notification");setItems(old=>old.map(n=>n.id===id?{...n,read:true}:n));}catch(e){setError(e.message||"Unable to update notification");}}
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">UPDATES</p><h3>Notifications</h3></div><button className="text-button" onClick={load}>Refresh</button></div>{error&&<div className="error-message">{error}</div>}{loading?<div className="empty-state">Loading notifications...</div>:items.length===0?<div className="empty-state"><strong>You're all caught up</strong><span>Important ticket and campus updates will appear here.</span></div>:<div className="ticket-list">{items.map(n=><div className="ticket-row" key={n.id}><div><strong>{n.read?"":"● "}{n.message}</strong><span>{new Date(n.createdAt).toLocaleString()}</span></div>{!n.read&&<button className="text-button" onClick={()=>markRead(n.id)}>Mark read</button>}</div>)}</div>}</section>;
}


function ResourcesPage({token}) {
  const [resources,setResources]=useState([]); const [bookings,setBookings]=useState([]); const [resourceId,setResourceId]=useState(""); const [startTime,setStartTime]=useState(""); const [endTime,setEndTime]=useState(""); const [loading,setLoading]=useState(true); const [busy,setBusy]=useState(false); const [cancelBusy,setCancelBusy]=useState(0); const [error,setError]=useState(""); const [notice,setNotice]=useState("");
  async function load(){setLoading(true);setError("");try{const headers={"Authorization":`Bearer ${token}`};const [rr,br]=await Promise.all([apiFetch(`${API_BASE_URL}/api/resources`,{headers}),apiFetch(`${API_BASE_URL}/api/bookings/mine`,{headers})]);const [rd,bd]=await Promise.all([rr.json(),br.json()]);if(!rr.ok||!rd.success)throw new Error(rd.message||"Unable to load resources");if(!br.ok||!bd.success)throw new Error(bd.message||"Unable to load bookings");setResources(rd.resources||[]);setBookings(bd.bookings||[]);setResourceId(old=>old||String((rd.resources||[])[0]?.id||""));}catch(e){setError(e.message||"Unable to load resource inventory");}finally{setLoading(false);}}
  useEffect(()=>{load();},[token]);
  async function submit(e){e.preventDefault();setBusy(true);setError("");setNotice("");try{const r=await apiFetch(`${API_BASE_URL}/api/bookings`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({resourceId:Number(resourceId),startTime,endTime})});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Booking failed");setNotice("Booking confirmed.");setStartTime("");setEndTime("");await load();}catch(e){setError(e.message||"Unable to create booking");}finally{setBusy(false);}}
  async function cancelBooking(id){setCancelBusy(id);setError("");setNotice("");try{const r=await apiFetch(`${API_BASE_URL}/api/bookings/${id}/cancel`,{method:"POST",headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Cancellation failed");setNotice("Booking cancelled.");await load();}catch(e){setError(e.message||"Cancellation failed");}finally{setCancelBusy(0);}}
  return <div className="page-stack"><section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">CAMPUS RESOURCES</p><h3>Book a resource</h3></div><button className="text-button" onClick={load}>Refresh</button></div>{error&&<div className="error-message">{error}</div>}{notice&&<div className="success-message">{notice}</div>}{loading?<div className="empty-state">Loading available resources...</div>:resources.length===0?<div className="empty-state"><strong>No resources available</strong><span>There are no resources available for booking right now.</span></div>:<form className="issue-form" onSubmit={submit}><label>Resource<select value={resourceId} onChange={e=>setResourceId(e.target.value)} required>{resources.map(r=><option value={r.id} key={r.id}>{r.name} · {r.type} · capacity {r.quantity}</option>)}</select></label><div className="form-row"><label>Start time<input type="datetime-local" value={startTime} onChange={e=>setStartTime(e.target.value)} required/></label><label>End time<input type="datetime-local" value={endTime} onChange={e=>setEndTime(e.target.value)} required/></label></div><button className="primary-button" disabled={busy}>{busy?"Booking...":"Confirm booking"}</button></form>}</section><section className="panel"><div className="panel-heading"><div><p className="eyebrow">YOUR SCHEDULE</p><h3>My bookings</h3></div></div>{loading?<div className="empty-state">Loading bookings...</div>:bookings.length===0?<div className="empty-state"><strong>No bookings yet</strong><span>Confirmed reservations will appear here.</span></div>:<div className="ticket-list">{bookings.map(b=><div className="ticket-row" key={b.id}><div><strong>{b.resourceName}</strong><span>{b.type}</span></div><div className="booking-times"><span>{new Date(b.startTime.replace(" ","T")).toLocaleString()}</span><span>to {new Date(b.endTime.replace(" ","T")).toLocaleString()}</span><button className="text-button" disabled={cancelBusy===b.id} onClick={()=>cancelBooking(b.id)}>{cancelBusy===b.id?"Cancelling...":"Cancel booking"}</button></div></div>)}</div>}</section></div>;
}

function EmergencyPage({token,user}) {
  const [items,setItems]=useState([]); const [loading,setLoading]=useState(true); const [error,setError]=useState(""); const [title,setTitle]=useState(""); const [message,setMessage]=useState(""); const [busy,setBusy]=useState(false); const [notice,setNotice]=useState("");
  async function load(){setLoading(true);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/emergencies`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load emergency feed");setItems(d.emergencies||[]);}catch(e){setError(e.message||"Unable to load emergency feed");}finally{setLoading(false);}}
  useEffect(()=>{load();},[token]);
  async function publish(e){e.preventDefault();setBusy(true);setError("");setNotice("");try{const r=await apiFetch(`${API_BASE_URL}/api/emergencies`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({title,message})});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to publish emergency");setNotice("Emergency broadcast sent to campus users.");setTitle("");setMessage("");await load();}catch(e){setError(e.message||"Unable to publish emergency");}finally{setBusy(false);}}
  return <div className="page-stack">{user.role==="ADMIN"&&<section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">ADMINISTRATOR ONLY</p><h3>Broadcast emergency</h3></div></div><form className="issue-form" onSubmit={publish}><label>Title<input value={title} onChange={e=>setTitle(e.target.value)} maxLength="200" required placeholder="e.g. Evacuate Block A"/></label><label>Instructions<textarea value={message} onChange={e=>setMessage(e.target.value)} maxLength="3000" rows="4" required placeholder="Share clear, actionable instructions."/></label>{notice&&<div className="success-message">{notice}</div>}{error&&<div className="error-message">{error}</div>}<button className="primary-button" disabled={busy}>{busy?"Broadcasting...":"Send emergency alert"}</button></form></section>}<section className="panel"><div className="panel-heading"><div><p className="eyebrow">CAMPUS SAFETY</p><h3>Emergency updates</h3></div><button className="text-button" onClick={load}>Refresh</button></div>{error&&user.role!=="ADMIN"&&<div className="error-message">{error}</div>}{loading?<div className="empty-state">Loading emergency updates...</div>:items.length===0?<div className="empty-state"><strong>No emergency announcements</strong><span>New campus safety updates will be shown here.</span></div>:<div className="ticket-list">{items.map(item=><article className="emergency-item" key={item.id}><div className="emergency-heading"><strong>{item.title}</strong><span>{new Date(item.createdAt).toLocaleString()}</span></div><p>{item.message}</p><small>Published by {item.createdBy}</small></article>)}</div>}</section></div>;
}



function FacultyWorkManagement({token}) {
  const [faculty,setFaculty]=useState([]);
  const [assignments,setAssignments]=useState([]);
  const [invite,setInvite]=useState({name:"",email:"",department:""});
  const [task,setTask]=useState({title:"",description:"",assignedTo:"",dueAt:""});
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [notice,setNotice]=useState("");
  async function load() {
    setLoading(true); setError("");
    try {
      const headers={Authorization:`Bearer ${token}`};
      const [fr,ar]=await Promise.all([
        apiFetch(`${API_BASE_URL}/api/admin/faculty`,{headers}),
        apiFetch(`${API_BASE_URL}/api/admin/work-assignments`,{headers})
      ]);
      const [fd,ad]=await Promise.all([fr.json(),ar.json()]);
      if(!fr.ok||!fd.success) throw new Error(fd.message||"Unable to load faculty");
      if(!ar.ok||!ad.success) throw new Error(ad.message||"Unable to load assignments");
      setFaculty(fd.faculty||[]); setAssignments(ad.assignments||[]);
      setTask(old=>({...old,assignedTo:old.assignedTo||String((fd.faculty||[]).find(x=>x.available)?.id||"")}));
    } catch(e) { setError(e.message||"Unable to load faculty work"); }
    finally { setLoading(false); }
  }
  useEffect(()=>{load();},[token]);
  async function sendInvite(e) {
    e.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const r=await apiFetch(`${API_BASE_URL}/api/admin/faculty-invitations`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify(invite)});
      const d=await r.json(); if(!r.ok||!d.success) throw new Error(d.message||"Unable to send invitation");
      setNotice("Invitation email sent. The professor has 72 hours to set a password.");
      setInvite({name:"",email:"",department:""}); await load();
    } catch(e) { setError(e.message||"Unable to send invitation"); }
    finally { setBusy(false); }
  }
  async function createTask(e) {
    e.preventDefault(); setBusy(true); setError(""); setNotice("");
    try {
      const payload={...task,assignedTo:Number(task.assignedTo),dueAt:task.dueAt?new Date(task.dueAt).toISOString():""};
      const r=await apiFetch(`${API_BASE_URL}/api/admin/work-assignments`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify(payload)});
      const d=await r.json(); if(!r.ok||!d.success) throw new Error(d.message||"Unable to assign work");
      setNotice("Work assigned successfully."); setTask(old=>({...old,title:"",description:"",dueAt:""})); await load();
    } catch(e) { setError(e.message||"Unable to assign work"); }
    finally { setBusy(false); }
  }
  const completed=assignments.filter(a=>a.status==="COMPLETED").length;
  const inProgress=assignments.filter(a=>a.status==="IN_PROGRESS").length;
  const overdue=assignments.filter(a=>a.status!=="COMPLETED"&&a.dueAt&&new Date(a.dueAt)<new Date()).length;
  return <div className="page-stack">
    <section className="stats-grid work-stats">
      <article className="stat-card"><span>Faculty</span><strong>{faculty.length}</strong><small>Professor accounts</small></article>
      <article className="stat-card"><span>Total assignments</span><strong>{assignments.length}</strong><small>All faculty work</small></article>
      <article className="stat-card"><span>In progress</span><strong>{inProgress}</strong><small>Currently active</small></article>
      <article className="stat-card"><span>Overdue</span><strong>{overdue}</strong><small>Past deadline</small></article>
    </section>
    {error&&<div className="error-message">{error}</div>}{notice&&<div className="success-message">{notice}</div>}
    <div className="content-grid work-forms">
      <section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">FACULTY ONBOARDING</p><h3>Invite a professor</h3></div></div>
        <form className="issue-form" onSubmit={sendInvite}>
          <label>Professor name<input value={invite.name} onChange={e=>setInvite({...invite,name:e.target.value})} maxLength="100" required placeholder="Full name"/></label>
          <label>Professor email<input type="email" value={invite.email} onChange={e=>setInvite({...invite,email:e.target.value})} maxLength="150" required placeholder="professor@college.edu"/></label>
          <label>Department<input value={invite.department} onChange={e=>setInvite({...invite,department:e.target.value})} maxLength="100" placeholder="Department (optional)"/></label>
          <p className="field-hint">An email invitation is sent. The link expires after 72 hours, and the professor creates their own password.</p>
          <button className="primary-button" disabled={busy}>{busy?"Sending...":"Send invitation"}</button>
        </form>
      </section>
      <section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">WORK ALLOCATION</p><h3>Assign work</h3></div></div>
        <form className="issue-form" onSubmit={createTask}>
          <label>Work title<input value={task.title} onChange={e=>setTask({...task,title:e.target.value})} maxLength="200" required placeholder="e.g. Submit department report"/></label>
          <label>Assign to<select value={task.assignedTo} onChange={e=>setTask({...task,assignedTo:e.target.value})} required><option value="">Choose professor</option>{faculty.filter(f=>f.available).map(f=><option key={f.id} value={f.id}>{f.name} · {f.department||"No department"}</option>)}</select></label>
          <label>Deadline<input type="datetime-local" value={task.dueAt} onChange={e=>setTask({...task,dueAt:e.target.value})}/></label>
          <label>Description<textarea value={task.description} onChange={e=>setTask({...task,description:e.target.value})} maxLength="5000" rows="3" required placeholder="Describe the expected outcome"/></label>
          <button className="primary-button" disabled={busy||faculty.filter(f=>f.available).length===0}>{busy?"Saving...":"Assign work"}</button>
        </form>
      </section>
    </div>
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">FACULTY OVERSIGHT</p><h3>Work progress</h3></div><button className="text-button" onClick={load} disabled={loading}>Refresh</button></div>
      {loading?<div className="empty-state">Loading assignments...</div>:assignments.length===0?<div className="empty-state"><strong>No work assigned yet</strong><span>Invite a professor and assign the first task.</span></div>:<div className="work-list">{assignments.map(a=><article className="work-card" key={a.id}>
        <div className="work-card-heading"><div><strong>{a.title}</strong><span>{a.facultyName} · {a.facultyEmail}</span></div><span className={`badge ${a.status==="COMPLETED"?"success":a.status==="IN_PROGRESS"?"active":"neutral"}`}>{a.status.replace("_"," ")}</span></div>
        <p>{a.description}</p>
        <div className="work-progress-label"><span>Progress</span><strong>{a.progressPercent}%</strong></div>
        <div className="progress-track" role="progressbar" aria-valuenow={a.progressPercent} aria-valuemin="0" aria-valuemax="100" aria-label={`Progress for ${a.title}`}><span style={{width:`${a.progressPercent}%`}}/></div>
        <div className="work-card-footer"><span>{a.dueAt?`Due ${new Date(a.dueAt).toLocaleString()}`:"No deadline"}</span>{a.dueAt&&a.status!=="COMPLETED"&&new Date(a.dueAt)<new Date()&&<span className="overdue-label">Overdue</span>}</div>
        {a.progressNote&&<p className="work-note">Update: {a.progressNote}</p>}
      </article>)}</div>}
      <div className="work-summary">{completed} completed · {inProgress} in progress · {assignments.length-completed-inProgress} pending</div>
    </section>
  </div>;
}

function FacultyWorkPage({token}) {
  const [assignments,setAssignments]=useState([]);
  const [drafts,setDrafts]=useState({});
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(0);
  const [error,setError]=useState("");
  const [notice,setNotice]=useState("");
  async function load() {
    setLoading(true);setError("");
    try {
      const r=await apiFetch(`${API_BASE_URL}/api/faculty/work-assignments`,{headers:{Authorization:`Bearer ${token}`}});
      const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load your work");
      setAssignments(d.assignments||[]);
      setDrafts(old=>Object.fromEntries((d.assignments||[]).map(a=>[a.id,old[a.id]||{status:a.status,progressPercent:String(a.progressPercent),progressNote:a.progressNote||""}])));
    } catch(e) {setError(e.message||"Unable to load your work");}
    finally {setLoading(false);}
  }
  useEffect(()=>{load();},[token]);
  async function save(id) {
    const draft=drafts[id];setBusy(id);setError("");setNotice("");
    try {
      const r=await apiFetch(`${API_BASE_URL}/api/faculty/work-assignments/${id}/progress`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${token}`},body:JSON.stringify({...draft,progressPercent:Number(draft.progressPercent)})});
      const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to update progress");
      setNotice("Progress saved.");await load();
    } catch(e) {setError(e.message||"Unable to update progress");}
    finally {setBusy(0);}
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">MY RESPONSIBILITIES</p><h3>Assigned work</h3></div><button className="text-button" onClick={load} disabled={loading}>Refresh</button></div>
    {error&&<div className="error-message">{error}</div>}{notice&&<div className="success-message">{notice}</div>}
    {loading?<div className="empty-state">Loading assigned work...</div>:assignments.length===0?<div className="empty-state"><strong>No work assigned</strong><span>New assignments from the HOD will appear here.</span></div>:<div className="work-list">{assignments.map(a=>{const draft=drafts[a.id]||{status:a.status,progressPercent:String(a.progressPercent),progressNote:a.progressNote||""};return <article className="work-card faculty-work-card" key={a.id}>
      <div className="work-card-heading"><div><strong>{a.title}</strong><span>{a.dueAt?`Due ${new Date(a.dueAt).toLocaleString()}`:"No deadline"}</span></div><span className={`badge ${a.status==="COMPLETED"?"success":a.status==="IN_PROGRESS"?"active":"neutral"}`}>{a.status.replace("_"," ")}</span></div>
      <p>{a.description}</p><div className="work-progress-label"><span>Current progress</span><strong>{a.progressPercent}%</strong></div><div className="progress-track" role="progressbar" aria-valuenow={a.progressPercent} aria-valuemin="0" aria-valuemax="100"><span style={{width:`${a.progressPercent}%`}}/></div>
      <div className="faculty-progress-form"><label>Status<select value={draft.status} onChange={e=>setDrafts(old=>({...old,[a.id]:{...draft,status:e.target.value,progressPercent:e.target.value==="COMPLETED"?"100":draft.progressPercent}}))}><option value="PENDING">Pending</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option></select></label><label>Progress: {draft.progressPercent}%<input type="range" min="0" max="100" step="5" value={draft.progressPercent} onChange={e=>setDrafts(old=>({...old,[a.id]:{...draft,progressPercent:e.target.value,status:Number(e.target.value)===100?"COMPLETED":draft.status==="COMPLETED"?"IN_PROGRESS":draft.status}}))}/></label><label>Progress note<textarea rows="2" maxLength="2000" value={draft.progressNote} onChange={e=>setDrafts(old=>({...old,[a.id]:{...draft,progressNote:e.target.value}}))} placeholder="Brief update for the HOD"/></label><button className="primary-button" disabled={busy===a.id} onClick={()=>save(a.id)}>{busy===a.id?"Saving...":"Save progress"}</button></div>
    </article>})}</div>}
  </section>;
}

function FacultyInvitationAcceptance() {
  const invitationToken=new URLSearchParams(window.location.search).get("facultyInvite")||"";
  const [password,setPassword]=useState("");
  const [confirm,setConfirm]=useState("");
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [success,setSuccess]=useState(false);
  async function submit(e) {
    e.preventDefault();setError("");
    if(password!==confirm){setError("Passwords do not match.");return;}
    if(password.length<12){setError("Use at least 12 characters for your password.");return;}
    setBusy(true);
    try {
      const r=await apiFetch(`${API_BASE_URL}/api/faculty-invitations/accept`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({token:invitationToken,password})});
      const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to accept invitation");
      setSuccess(true);setPassword("");setConfirm("");
      sessionStorage.removeItem("campusos.user");sessionStorage.removeItem("campusos.token");
      window.history.replaceState({},document.title,window.location.pathname);
    } catch(e) {setError(e.message||"Unable to accept invitation");}
    finally {setBusy(false);}
  }
  return <div className="login-shell"><div className="login-card"><div className="brand login-brand"><div className="brand-mark">C</div><div><strong>CAMPUSOS</strong><span>Faculty access</span></div></div>{success?<><p className="eyebrow">ACCOUNT READY</p><h1>You're all set.</h1><p className="login-copy">Your faculty account has been created. Sign in with the invited email and your new password.</p><button className="primary-button login-button" onClick={()=>{window.location.href="/";}}>Go to sign in</button></>:<><p className="eyebrow">FACULTY INVITATION</p><h1>Set your password.</h1><p className="login-copy">Choose a strong password with at least 12 characters. This invitation expires after 72 hours.</p>{!invitationToken?<div className="error-message">This invitation link is missing its token. Please open the full link from your email.</div>:<form className="login-form" onSubmit={submit}><label>New password<input type="password" autoComplete="new-password" minLength="12" maxLength="128" value={password} onChange={e=>setPassword(e.target.value)} required/></label><label>Confirm password<input type="password" autoComplete="new-password" minLength="12" maxLength="128" value={confirm} onChange={e=>setConfirm(e.target.value)} required/></label>{error&&<div className="error-message">{error}</div>}<button className="primary-button login-button" disabled={busy}>{busy?"Creating account...":"Create faculty account"}</button></form>}</>}</div></div>;
}

function AdminOverview({token}) {
  const [data,setData]=useState(null); const [error,setError]=useState(""); const [loading,setLoading]=useState(true); const [exporting,setExporting]=useState(false);
  async function load(){setLoading(true);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/overview`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load admin overview");setData(d.overview);}catch(e){setError(e.message||"Unable to load admin overview");}finally{setLoading(false);}}
  useEffect(()=>{load();},[token]);
  async function exportTickets(){setExporting(true);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/reports/tickets.csv`,{headers:{"Authorization":`Bearer ${token}`}});if(!r.ok){let message="Unable to export ticket report";try{const d=await r.json();message=d.message||message;}catch{}throw new Error(message);}const blob=await r.blob();const url=URL.createObjectURL(blob);const link=document.createElement("a");link.href=url;link.download="campusos-tickets.csv";document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){setError(e.message||"Unable to export ticket report");}finally{setExporting(false);}}
  return <section className="page-stack"><div className="panel-heading"><div><p className="eyebrow">ADMINISTRATION</p><h3>Campus overview</h3></div><div><button className="text-button" onClick={exportTickets} disabled={exporting}>{exporting?"Exporting...":"Export tickets CSV"}</button><button className="text-button" onClick={load}>Refresh</button></div></div>{error&&<div className="error-message">{error}</div>}{loading?<div className="empty-state">Loading campus metrics...</div>:data&&<div className="stats-grid"><article className="stat-card"><span>Users</span><strong>{data.totalUsers}</strong><small>Registered accounts</small></article><article className="stat-card"><span>Tickets</span><strong>{data.totalTickets}</strong><small>All time</small></article><article className="stat-card"><span>Active tickets</span><strong>{data.activeTickets}</strong><small>Need attention</small></article><article className="stat-card"><span>Resolved</span><strong>{data.resolvedTickets}</strong><small>Completed tickets</small></article><article className="stat-card"><span>Critical issues</span><strong>{data.criticalIssues}</strong><small>Review immediately</small></article><article className="stat-card"><span>Available staff</span><strong>{data.availableStaff}</strong><small>Can receive assignments</small></article></div>}<div className="panel"><p className="panel-copy">Use Tickets to assign open requests and monitor staff progress, Users to review accounts and manage staff availability, and Emergency to send campus-wide alerts.</p></div></section>;
}

function AdminUsers({token}) {
  const [users,setUsers]=useState([]); const [loading,setLoading]=useState(true); const [error,setError]=useState(""); const [notice,setNotice]=useState(""); const [busy,setBusy]=useState(0);
  const [page,setPage]=useState(1); const [pageSize]=useState(50); const [totalUsers,setTotalUsers]=useState(0); const [totalPages,setTotalPages]=useState(1);
  const [form,setForm]=useState({name:"",email:"",password:"",role:"STUDENT",registerNumber:"",department:"",specialization:""});
  async function load(requestedPage=page){setLoading(true);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/users?page=${requestedPage}&pageSize=${pageSize}`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load users");setUsers(d.users||[]);setPage(d.page||requestedPage);setTotalUsers(d.totalUsers||0);setTotalPages(d.totalPages||1);}catch(e){setError(e.message||"Unable to load users");}finally{setLoading(false);}}
  useEffect(()=>{load(1);},[token]);
  function change(e){setForm(old=>({...old,[e.target.name]:e.target.value}));}
  async function createUser(e){e.preventDefault();setBusy(-1);setError("");setNotice("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/users`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify(form)});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to create user");setNotice(`User created successfully (ID ${d.userId}). Share the initial password securely.`);setForm({name:"",email:"",password:"",role:"STUDENT",registerNumber:"",department:"",specialization:""});await load();}catch(e){setError(e.message||"Unable to create user");}finally{setBusy(0);}}
  async function toggle(user){setBusy(user.id);setError("");try{const r=await apiFetch(`${API_BASE_URL}/api/admin/users/${user.id}/availability`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({available:!user.available})});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to update staff");setUsers(old=>old.map(item=>item.id===user.id?{...item,available:!item.available}:item));}catch(e){setError(e.message||"Unable to update staff");}finally{setBusy(0);}}
  async function changeAccountAccess(user){
    const active=!(user.available!==false);
    const action=active?"restore access to":"remove access from";
    if(!window.confirm(`Are you sure you want to ${action} ${user.name} ( ${user.email} )? Their existing campus records will be kept.`)) return;
    setBusy(user.id);setError("");setNotice("");
    try{
      const r=await apiFetch(`${API_BASE_URL}/api/admin/users/${user.id}/access`,{method:"POST",headers:{"Content-Type":"application/json","Authorization":`Bearer ${token}`},body:JSON.stringify({active})});
      const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to change account access");
      setNotice(d.message||"Account access updated.");
      await load(page);
    }catch(e){setError(e.message||"Unable to change account access");}
    finally{setBusy(0);}
  }
  return <div className="page-stack"><section className="form-panel"><div className="panel-heading"><div><p className="eyebrow">ADMINISTRATION</p><h3>Create user account</h3></div></div><form className="issue-form" onSubmit={createUser}><div className="form-row"><label>Full name<input name="name" value={form.name} onChange={change} maxLength="100" required/></label><label>Email<input name="email" type="email" value={form.email} onChange={change} maxLength="150" required/></label></div><div className="form-row"><label>Role<select name="role" value={form.role} onChange={change}>{["STUDENT","FACULTY","MAINTENANCE","TECHNICIAN","ADMIN"].map(role=><option key={role}>{role}</option>)}</select></label><label>Initial password<input name="password" type="password" value={form.password} onChange={change} minLength="12" maxLength="128" autoComplete="new-password" required/><small>At least 12 characters; share securely.</small></label></div><div className="form-row"><label>Register number{form.role==="STUDENT"?" *":" (optional)"}<input name="registerNumber" value={form.registerNumber} onChange={change} maxLength="50" required={form.role==="STUDENT"}/></label><label>Department<input name="department" value={form.department} onChange={change} maxLength="100"/></label></div>{["MAINTENANCE","TECHNICIAN"].includes(form.role)&&<label>Specialization<input name="specialization" value={form.specialization} onChange={change} maxLength="100" placeholder="e.g. Electrical, Network, Plumbing"/></label>}{notice&&<div className="success-message">{notice}</div>}{error&&<div className="error-message">{error}</div>}<button className="primary-button" disabled={busy===-1}>{busy===-1?"Creating account...":"Create account"}</button></form></section><section className="panel"><div className="panel-heading"><div><p className="eyebrow">ADMINISTRATION</p><h3>User directory</h3><p className="panel-copy">{totalUsers} accounts · Page {page} of {totalPages}</p></div><div className="pagination-controls"><button className="text-button" disabled={loading||page<=1} onClick={()=>load(page-1)}>Previous</button><button className="text-button" disabled={loading} onClick={()=>load(page)}>Refresh</button><button className="text-button" disabled={loading||page>=totalPages} onClick={()=>load(page+1)}>Next</button></div></div>{loading?<div className="empty-state">Loading users...</div>:users.length===0?<div className="empty-state">No users found.</div>:<div className="user-list">{users.map(u=><div className="user-row" key={u.id}><div><strong>{u.name}</strong><span>{u.email}</span><span>{u.role}{u.department ? " · " + u.department : ""}{u.specialization ? " · " + u.specialization : ""}</span></div><div className="user-actions">{["MAINTENANCE","TECHNICIAN"].includes(u.role)?<><span className={u.available?"availability available":"availability unavailable"}>{u.available?"Available":"Unavailable"}</span><button className="text-button" disabled={busy===u.id} onClick={()=>toggle(u)}>{u.available?"Set unavailable":"Set available"}</button></>:<span className={["STUDENT","FACULTY"].includes(u.role)&&u.available===false?"availability unavailable":"badge neutral"}>{["STUDENT","FACULTY"].includes(u.role)&&u.available===false?"Access removed":u.role}</span>{["STUDENT","FACULTY"].includes(u.role)&&<button className="text-button" disabled={busy===u.id} onClick={()=>changeAccountAccess(u)}>{busy===u.id?"Saving...":u.available===false?"Restore access":"Remove access"}</button>}}</div></div>)}</div>}</section></div>;
}

function Dashboard({user,token,onLogout}){
  const [active,setActive]=useState("Overview"); const [tickets,setTickets]=useState([]); const [loadingTickets,setLoadingTickets]=useState(false); const [ticketError,setTicketError]=useState(""); const [ticketPage,setTicketPage]=useState(1); const [ticketSummary,setTicketSummary]=useState({total:0,openCount:0,resolvedCount:0});
  async function loadTickets(page=1){setLoadingTickets(true);setTicketError("");try{const r=await apiFetch(`${API_BASE_URL}/api/tickets/reporter/${user.id}?page=${page}`,{headers:{"Authorization":`Bearer ${token}`}});const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||"Unable to load tickets");setTickets(d.tickets||[]);setTicketPage(d.page||page);setTicketSummary({total:d.total||0,openCount:d.openCount||0,resolvedCount:d.resolvedCount||0});}catch(err){setTicketError(err.message||"Unable to connect to CAMPUSOS");}finally{setLoadingTickets(false);}}
  useEffect(()=>{if(["STUDENT","FACULTY"].includes(user.role)) loadTickets(1);},[user.id,user.role,token]);
  const visibleNavItems = user.role === "ADMIN" ? [["Overview","⌂"],["HOD Work","▤"],["Tickets","✓"],["Users","♙"],["Resources","▣"],["Notifications","◌"],["Emergency","!"]] : user.role === "FACULTY" ? [["Overview","⌂"],["My Work","▤"],["Tickets","✓"],["Report Issue","+"],["Resources","▣"],["Notifications","◌"],["Emergency","!"]] : ["MAINTENANCE","TECHNICIAN"].includes(user.role) ? [["Overview","⌂"],["Tickets","✓"],["Resources","▣"],["Notifications","◌"],["Emergency","!"]] : navItems;
  const open=ticketSummary.openCount;
  const resolved=ticketSummary.resolvedCount;
  const page = active === "HOD Work" && user.role === "ADMIN"
    ? <FacultyWorkManagement token={token}/>
    : active === "My Work" && user.role === "FACULTY"
      ? <FacultyWorkPage token={token}/>
      : active === "Users" && user.role === "ADMIN"
    ? <AdminUsers token={token}/>
    : active === "Overview" && user.role === "ADMIN"
      ? <AdminOverview token={token}/>
      : active === "Report Issue"
    ? <ReportIssue user={user} token={token} onCreated={loadTickets}/>
    : active === "Tickets"
      ? (["MAINTENANCE","TECHNICIAN","ADMIN"].includes(user.role)
          ? <StaffQueue token={token} user={user}/>
          : <MyTicketsPage tickets={tickets} loading={loadingTickets} error={ticketError} onRefresh={()=>loadTickets(1)} token={token} page={ticketPage} totalTickets={ticketSummary.total} onPageChange={loadTickets}/>)
      : active === "Notifications"
        ? <NotificationsPage token={token}/>
        : active === "Emergency"
          ? <EmergencyPage token={token} user={user}/>
          : active === "Resources"
            ? <ResourcesPage token={token}/>
            : <><section className="stats-grid"><article className="stat-card"><span>My tickets</span><strong>{ticketSummary.total}</strong><small>Total reported</small></article><article className="stat-card"><span>Open</span><strong>{open}</strong><small>Being handled</small></article><article className="stat-card"><span>Resolved</span><strong>{resolved}</strong><small>Completed</small></article><article className="stat-card"><span>Role</span><strong>{user.role}</strong><small>Current access</small></article></section><section className="content-grid"><article className="panel"><div className="panel-heading"><div><p className="eyebrow">MY REQUESTS</p><h3>Latest tickets</h3></div><button className="text-button" onClick={loadTickets}>Refresh</button></div>{ticketError?<div className="error-message">{ticketError} <button className="text-button" onClick={loadTickets}>Try again</button></div>:loadingTickets?<div className="empty-state">Loading tickets...</div>:tickets.length===0?<div className="empty-state"><div className="empty-icon">+</div><strong>No tickets yet</strong><span>Use Report Issue to create your first campus request.</span></div>:<div className="ticket-list">{tickets.slice(0,8).map(t=><div className="ticket-row" key={t.id}><div><strong>#{t.id} · {t.title}</strong><span>{t.category} · {t.location||"Location not specified"}</span></div><div className="ticket-meta"><span className={`priority ${t.priority.toLowerCase()}`}>{t.priority}</span><span className="badge neutral">{t.status}</span></div></div>)}</div>}</article><article className="panel"><div className="panel-heading"><div><p className="eyebrow">QUICK ACTION</p><h3>Need help?</h3></div></div><p className="panel-copy">Report maintenance, network, electrical, plumbing or security issues. CAMPUSOS automatically assigns a priority and available technician.</p><button className="primary-button" onClick={()=>setActive("Report Issue")}>Report an issue</button></article></section></>
  return <div className="app-shell"><aside className="sidebar"><div className="brand"><div className="brand-mark">C</div><div><strong>CAMPUSOS</strong><span>Campus Operations</span></div></div><nav className="nav-list">{visibleNavItems.map(([label,icon])=><button key={label} className={active===label?"nav-item active":"nav-item"} onClick={()=>setActive(label)}><span className="nav-icon">{icon}</span><span>{label}</span></button>)}</nav><div className="sidebar-footer"><span className="status-dot"/> Campus system online</div></aside><main className="main-content"><header className="topbar"><div><p className="eyebrow">Campus Operations</p><h1>{active}</h1></div><div className="profile"><div className="avatar">{user.name.split(" ").map(x=>x[0]).slice(0,2).join("")}</div><div><strong>{user.name}</strong><span>{user.role}</span></div><button className="text-button" onClick={onLogout}>Logout</button></div></header>{active==="Overview"&&<section className="hero-card"><div><p className="eyebrow">CAMPUSOS CONTROL CENTER</p><h2>Everything happening on campus, in one place.</h2><p className="hero-copy">Report issues, track tickets, manage resources and stay informed about campus operations.</p></div><div className="hero-orb"><span/><span/><span/></div></section>}{page}</main></div>;
}

export default function App(){const [user,setUser]=useState(()=>{try{return JSON.parse(sessionStorage.getItem("campusos.user")||"null")}catch{return null}});const [token,setToken]=useState(()=>sessionStorage.getItem("campusos.token")||"");function handleLogin(nextUser,nextToken){sessionStorage.setItem("campusos.user",JSON.stringify(nextUser));sessionStorage.setItem("campusos.token",nextToken);setUser(nextUser);setToken(nextToken)}async function logout(){const activeToken=token;try{if(activeToken)await apiFetch(`${API_BASE_URL}/api/auth/logout`,{method:"POST",headers:{"Authorization":`Bearer ${activeToken}`}})}catch{}finally{sessionStorage.removeItem("campusos.user");sessionStorage.removeItem("campusos.token");setUser(null);setToken("")}}if(new URLSearchParams(window.location.search).has("facultyInvite")) return <FacultyInvitationAcceptance/>;return user&&token?<Dashboard user={user} token={token} onLogout={logout}/>:<Login onLogin={handleLogin}/>;}