import { useState, useEffect, useCallback } from 'react';
import './amplifyConfig';
import './App.css';
import { API_URL } from './amplifyConfig';
import { signUp, signIn, signOut, getCurrentUser, fetchAuthSession } from 'aws-amplify/auth';

// ---- API helper: attaches the Cognito JWT to every request ----
async function api(path, options = {}) {
  const session = await fetchAuthSession();
  const token = session.tokens?.idToken?.toString();
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: token,
      ...(options.headers || {}),
    },
  });
  if (!res.ok) throw new Error(`API ${res.status}`);
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

function App() {
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState('signin');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const [tasks, setTasks] = useState([]);
  const [newDesc, setNewDesc] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getCurrentUser().then(setUser).catch(() => setUser(null));
  }, []);

  // ---- Task operations ----
  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api('/tasks');
      setTasks(Array.isArray(data) ? data : []);
    } catch (e) {
      setError('Failed to load tasks: ' + e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (user) loadTasks();
  }, [user, loadTasks]);

  async function createTask() {
    if (!newDesc.trim()) return;
    try {
      await api('/tasks', {
        method: 'POST',
        body: JSON.stringify({ description: newDesc, date: new Date().toISOString().slice(0, 10) }),
      });
      setNewDesc('');
      loadTasks();
    } catch (e) { setError('Create failed: ' + e.message); }
  }

  async function completeTask(taskId) {
    try {
      await api(`/tasks/${taskId}`, { method: 'PUT', body: JSON.stringify({ status: 'Completed' }) });
      loadTasks();
    } catch (e) { setError('Update failed: ' + e.message); }
  }

  async function deleteTask(taskId) {
    try {
      await api(`/tasks/${taskId}`, { method: 'DELETE' });
      loadTasks();
    } catch (e) { setError('Delete failed: ' + e.message); }
  }

  // ---- Auth ----
  async function doSignIn() {
    await signIn({ username: email, password });
    setUser(await getCurrentUser());
  }
  async function handleSignUp() {
    setError(''); setBusy(true);
    try {
      await signUp({ username: email, password, options: { userAttributes: { email } } });
      await doSignIn();
    } catch (e) { setError(e.message || String(e)); }
    finally { setBusy(false); }
  }
  async function handleSignIn() {
    setError(''); setBusy(true);
    try { await doSignIn(); }
    catch (e) { setError(e.message || String(e)); }
    finally { setBusy(false); }
  }
  async function handleSignOut() {
    await signOut();
    setUser(null);
    setTasks([]);
  }

  // ---- Task view ----
  if (user) {
    const pending = tasks.filter((t) => t.status === 'Pending');
    const completed = tasks.filter((t) => t.status === 'Completed');
    const expired = tasks.filter((t) => t.status === 'Expired');

    const Section = ({ title, items, statusClass }) => (
      <div className="section">
        <div className="section-title">
          {title} <span className="count-badge">{items.length}</span>
        </div>
        {items.length === 0 ? (
          <p className="empty">No {title.toLowerCase()} tasks</p>
        ) : (
          items.map((t) => (
            <div key={t.taskId} className={`task ${statusClass}`}>
              <div className={`status-${t.status.toLowerCase()}`} style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
                <span className="status-dot"></span>
                <div className="task-info">
                  <div className="task-desc">{t.description}</div>
                  <div className="task-meta">{t.date} · {t.status}</div>
                </div>
              </div>
              <div className="task-actions">
                {t.status === 'Pending' && (
                  <button className="btn-sm" onClick={() => completeTask(t.taskId)}>Complete</button>
                )}
                <button className="btn-sm danger" onClick={() => deleteTask(t.taskId)}>Delete</button>
              </div>
            </div>
          ))
        )}
      </div>
    );

    return (
      <div className="app-wrap">
        <div className="app-header">
          <div>
            <h1>My Tasks</h1>
            <div className="user-email">{user.signInDetails?.loginId || user.username}</div>
          </div>
          <button className="btn-ghost" onClick={handleSignOut}>Sign out</button>
        </div>

        <div className="create-row">
          <input
            placeholder="What needs doing?"
            value={newDesc}
            onChange={(e) => setNewDesc(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && createTask()}
          />
          <button className="btn-primary" onClick={createTask}>Add Task</button>
        </div>

        {error && <div className="error">{error}</div>}
        {loading && <p className="empty">Loading…</p>}

        <Section title="Pending" items={pending} statusClass="pending" />
        <Section title="Completed" items={completed} statusClass="completed" />
        <Section title="Expired" items={expired} statusClass="expired" />
      </div>
    );
  }

  // ---- Auth view ----
  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <h1>To-Do</h1>
        <p className="subtitle">
          {mode === 'signin' ? 'Sign in to manage your tasks' : 'Create an account to get started'}
        </p>
        <div className="field">
          <label>Email</label>
          <input type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label>Password</label>
          <input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        {error && <div className="error">{error}</div>}
        <button className="btn-primary" disabled={busy} onClick={mode === 'signin' ? handleSignIn : handleSignUp}>
          {busy ? 'Please wait…' : mode === 'signin' ? 'Sign In' : 'Sign Up'}
        </button>
        <div className="switch-mode">
          {mode === 'signin' ? "Don't have an account? " : 'Already have an account? '}
          <a onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(''); }}>
            {mode === 'signin' ? 'Sign up' : 'Sign in'}
          </a>
        </div>
      </div>
    </div>
  );
}

export default App;
