import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { RequireAuth } from './auth/RequireAuth';
import { ProcessListPage } from './pages/ProcessListPage';
import { TaskListPage } from './pages/TaskListPage';
import { TemplatesPage } from './pages/TemplatesPage';

function App() {
  return (
    <RequireAuth>
      <AppLayout>
        <Routes>
          <Route path="/" element={<Navigate to="/processes" replace />} />
          <Route path="/processes" element={<ProcessListPage />} />
          <Route path="/tasks" element={<TaskListPage />} />
          <Route path="/templates" element={<TemplatesPage />} />
          <Route path="*" element={<Navigate to="/processes" replace />} />
        </Routes>
      </AppLayout>
    </RequireAuth>
  );
}

export default App;
