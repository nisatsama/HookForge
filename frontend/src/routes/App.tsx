import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { Activity, ChartNoAxesCombined, ChevronDown, KeyRound, LogOut, Radio, Settings2, ShieldCheck, Webhook, Zap } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { setCredential } from '../api/client';
import { ApiKeys, Dashboard, DeliveryDetail, Deliveries, Events, SettingsPage, SignIn } from '../pages/Pages';
import { EndpointPage } from '../pages/EndpointPage';

const nav = [
  { to: '/', label: 'Overview', icon: ChartNoAxesCombined },
  { to: '/endpoints', label: 'Endpoints', icon: Webhook },
  { to: '/events', label: 'Events', icon: Radio },
  { to: '/deliveries', label: 'Deliveries', icon: Activity },
  { to: '/keys', label: 'API keys', icon: KeyRound },
  { to: '/settings', label: 'Settings', icon: Settings2 },
];

export function App() {
  const { token, signOut } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => {
    setCredential(token);
    const handler = () => signOut();
    window.addEventListener('hookforge:unauthorized', handler);
    return () => window.removeEventListener('hookforge:unauthorized', handler);
  }, [token, signOut]);

  if (!token) return <Routes><Route path="*" element={<SignIn />} /></Routes>;

  return <div className={`shell ${collapsed ? 'collapsed' : ''}`}>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark"><Zap size={18} fill="currentColor" /></span>{!collapsed && <span>hookforge<span className="brand-dot">.</span></span>}<button className="collapse" onClick={() => setCollapsed(!collapsed)} aria-label="Collapse navigation"><ChevronDown size={15}/></button></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav>{nav.map(item => <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><item.icon size={17}/><span>{item.label}</span></NavLink>)}</nav>
      <div className="sidebar-bottom"><button className="profile" onClick={signOut}><span className="avatar">HF</span><span className="profile-copy"><b>API key session</b><small>Sign out</small></span><LogOut size={15}/></button></div>
    </aside>
    <main className="main">
      <header className="topbar"><div className="crumb"><span>Workspace</span><span className="slash">/</span><b>Production</b><ChevronDown size={13}/></div><div className="top-actions"><span className="key-chip"><ShieldCheck size={14}/> API key auth</span><div className="top-avatar">HF</div></div></header>
      <div className="page-wrap"><Routes><Route path="/" element={<Dashboard/>}/><Route path="/endpoints" element={<EndpointPage/>}/><Route path="/events" element={<Events/>}/><Route path="/deliveries" element={<Deliveries/>}/><Route path="/deliveries/:id" element={<DeliveryDetail/>}/><Route path="/keys" element={<ApiKeys/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></div>
    </main>
  </div>;
}
