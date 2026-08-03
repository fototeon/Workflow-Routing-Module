import { Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { ROLES } from './auth/authConfig';
import { ProcessListPage } from './pages/ProcessListPage';
import { ProcessDetailPage } from './pages/ProcessDetailPage';
import { TaskListPage } from './pages/TaskListPage';
import { TemplatesPage } from './pages/TemplatesPage';
import { SlaPoliciesPage } from './pages/SlaPoliciesPage';
import { AnalyticsPage } from './pages/AnalyticsPage';

function App() {
  return (
    <RequireAuth>
      <AppLayout>
        <Routes>
          <Route path="/" element={<Navigate to="/processes" replace />} />
          <Route path="/processes" element={<ProcessListPage />} />
          <Route path="/processes/:id" element={<ProcessDetailPage />} />
          <Route
            path="/tasks"
            element={
              <RequireRole allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
                <TaskListPage />
              </RequireRole>
            }
          />
          <Route
            path="/analytics"
            element={
              <RequireRole allow={[ROLES.ANALYST, ROLES.MANAGER, ROLES.ADMIN]}>
                <AnalyticsPage />
              </RequireRole>
            }
          />
          <Route
            path="/templates"
            element={
              <RequireRole allow={[ROLES.ADMIN]}>
                <TemplatesPage />
              </RequireRole>
            }
          />
          <Route
            path="/sla-policies"
            element={
              <RequireRole allow={[ROLES.ADMIN]}>
                <SlaPoliciesPage />
              </RequireRole>
            }
          />
          <Route path="*" element={<Navigate to="/processes" replace />} />
        </Routes>
      </AppLayout>
    </RequireAuth>
  );
}

export default App;
