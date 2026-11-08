import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowUpRight, Edit2, Plus, ShieldCheck, Webhook, X } from 'lucide-react';
import { endpointsApi } from '../api/services';
import { messageOf } from '../api/client';
import type { Endpoint } from '../types/api';

const schema = z.object({ name:z.string().trim().min(1).max(150), url:z.string().url().refine(value=>value.startsWith('https://'),'Destination must use HTTPS') });
type FormValues = z.infer<typeof schema>;

export function EndpointPage() {
  const client=useQueryClient(),list=useQuery({queryKey:['endpoints'],queryFn:endpointsApi.list});
  const [editing,setEditing]=useState<Endpoint|null>(null),[creating,setCreating]=useState(false),[secret,setSecret]=useState(''),[copied,setCopied]=useState(false);
  const form=useForm<FormValues>({resolver:zodResolver(schema),defaultValues:{name:'',url:''}});
  const close=()=>{setEditing(null);setCreating(false);setSecret('');form.reset({name:'',url:''});};
  const create=useMutation({mutationFn:endpointsApi.create,onSuccess:result=>{setSecret(result.secret);void client.invalidateQueries({queryKey:['endpoints']});}});
  const update=useMutation({mutationFn:(input:FormValues&{id:string})=>endpointsApi.update(input.id,{name:input.name,url:input.url}),onSuccess:()=>{void client.invalidateQueries({queryKey:['endpoints']});close();}});
  const toggle=useMutation({mutationFn:({id,enabled}:{id:string;enabled:boolean})=>endpointsApi.status(id,enabled),onSuccess:()=>void client.invalidateQueries({queryKey:['endpoints']})});
  function openEdit(endpoint:Endpoint){setEditing(endpoint);setCreating(true);form.reset({name:endpoint.name,url:endpoint.url});}
  const submit=form.handleSubmit(value=>editing?update.mutate({...value,id:editing.id}):create.mutate(value));
  return <>
    <div className="page-title"><div><div className="eyebrow">CONFIGURATION / ENDPOINTS</div><h1>Webhook endpoints</h1><p>Manage destinations that receive signed webhook requests.</p></div><button className="primary" onClick={()=>{close();setCreating(true);}}><Plus size={16}/> Add endpoint</button></div>
    <div className="inline-stats"><span><b>{list.data?.length??'—'}</b> endpoints</span><span><i className="green-dot"/> {list.data?.filter(item=>item.status==='ACTIVE').length??'—'} active</span><span><ShieldCheck size={14}/> Signed requests</span></div>
    <section className="panel table-panel">{list.isLoading?<div className="loading"><span className="spinner"/> Loading live endpoints…</div>:list.isError?<div className="error-box">{messageOf(list.error)}</div>:list.data?.length?<div className="table-scroll"><table><thead><tr>{['ENDPOINT','DESTINATION URL','STATUS','CREATED','ACTIONS'].map(item=><th key={item}>{item}</th>)}</tr></thead><tbody>{list.data.map(endpoint=><tr key={endpoint.id}><td><div className="endpoint-name"><span className="endpoint-icon"><Webhook size={16}/></span><span><b>{endpoint.name}</b><small className="mono">{endpoint.id.slice(0,8)}</small></span></div></td><td className="mono destination">{endpoint.url}</td><td><span className={`state ${endpoint.status==='ACTIVE'?'good':'neutral'}`}><i/>{endpoint.status.toLowerCase()}</span></td><td className="muted">{new Intl.DateTimeFormat(undefined,{dateStyle:'medium'}).format(new Date(endpoint.createdAt))}</td><td><div className="row-actions"><button className="small-button" onClick={()=>openEdit(endpoint)}><Edit2 size={12}/> Edit</button><button className="small-button" disabled={toggle.isPending} onClick={()=>toggle.mutate({id:endpoint.id,enabled:endpoint.status!=='ACTIVE'})}>{endpoint.status==='ACTIVE'?'Disable':'Enable'}</button></div></td></tr>)}</tbody></table></div>:<div className="empty"><span className="empty-icon"><Webhook size={20}/></span><strong>No endpoints yet</strong><span>Register a destination to start receiving events.</span></div>}</section>
    {creating&&<div className="modal-backdrop"><div className="modal"><div className="modal-head"><div><div className="eyebrow">{editing?'ENDPOINT METADATA':'NEW DESTINATION'}</div><h2>{secret?'Signing secret':editing?'Edit endpoint':'Register endpoint'}</h2></div><button className="icon-btn" onClick={close}><X size={18}/></button></div>{secret?<><p className="form-hint">Copy this signing secret now. It cannot be retrieved later.</p><div className="secret-box"><code>{secret}</code><button className="small-button" onClick={()=>{void navigator.clipboard.writeText(secret);setCopied(true);}}>{copied?'Copied':'Copy'}</button></div><button className="primary full" onClick={close}>I’ve saved the secret</button></>:<form onSubmit={submit}><label>Endpoint name</label><input {...form.register('name')} placeholder="Production API"/>{form.formState.errors.name&&<small className="field-error">{form.formState.errors.name.message}</small>}<label>Destination URL</label><input {...form.register('url')} placeholder="https://api.example.com/hooks"/>{form.formState.errors.url&&<small className="field-error">{form.formState.errors.url.message}</small>}<div className="form-hint"><ShieldCheck size={14}/> HTTPS destinations only. Private and local addresses are blocked.</div>{(create.isError||update.isError)&&<div className="error-box">{messageOf(create.error||update.error)}</div>}<button className="primary full" disabled={create.isPending||update.isPending}>{editing?'Save changes':create.isPending?'Registering…':'Create endpoint'}<ArrowUpRight size={15}/></button></form>}</div></div>}
  </>;
}
