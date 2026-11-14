import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../context/AuthContext';
import { api } from '../api/client';
import { App } from './App';
import '../test/setup';

const metrics={total:76,pending:7,processing:0,retrying:7,succeeded:46,failed:9,dead:7};
const emptyPage={items:[],page:0,size:20,totalElements:0,totalPages:0,last:true};

function renderApp(path:string) {
  const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  return render(<MemoryRouter initialEntries={[path]}><QueryClientProvider client={client}><AuthProvider><App/></AuthProvider></QueryClientProvider></MemoryRouter>);
}

afterEach(()=>{ cleanup(); vi.restoreAllMocks(); });

describe('public and authenticated entry flows',()=>{
  it('lets a recruiter explore the public demo and navigate to API-key login',async()=>{
    const get=vi.spyOn(api,'get').mockImplementation(async (url)=>({data:url.endsWith('/metrics/summary')?metrics:url.endsWith('/endpoints')?[]:emptyPage} as never));
    const user=userEvent.setup();
    renderApp('/');
    expect((await screen.findAllByText('DEMO MODE · READ ONLY')).length).toBeGreaterThan(1);
    await user.click(screen.getByRole('link',{name:/login/i}));
    expect(await screen.findByRole('heading',{name:'Connect your workspace'})).toBeInTheDocument();
    expect(screen.getByLabelText('API key')).toBeInTheDocument();
    await waitFor(()=>expect(get).toHaveBeenCalledWith('/api/v1/demo/metrics/summary'));
    expect(get.mock.calls.every(([url])=>String(url).startsWith('/api/v1/demo/'))).toBe(true);
  });

  it('authenticates into the real tenant workspace and logout returns to the public demo',async()=>{
    const get=vi.spyOn(api,'get').mockImplementation(async (url)=>({data:String(url).endsWith('/endpoints')?[]:String(url).endsWith('/metrics/summary')?metrics:emptyPage} as never));
    const user=userEvent.setup();
    renderApp('/login');
    await user.type(screen.getByLabelText('API key'),'hf_live_real-tenant-key');
    await user.click(screen.getByRole('button',{name:/continue securely/i}));
    expect(await screen.findByText('API key auth')).toBeInTheDocument();
    expect(await screen.findByRole('heading',{name:'Good evening'})).toBeInTheDocument();
    expect(screen.queryByText('DEMO MODE · READ ONLY')).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/v1/metrics/summary');
    expect(get.mock.calls.some(([url])=>String(url).startsWith('/api/v1/demo/'))).toBe(false);
    await user.click(screen.getByRole('button',{name:/sign out/i}));
    expect((await screen.findAllByText('DEMO MODE · READ ONLY')).length).toBeGreaterThan(1);
  });
});
