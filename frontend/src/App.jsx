import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { useAuth } from './context/AuthContext';
import AnalyticsPage from './pages/AnalyticsPage';
import AuthPage from './pages/AuthPage';
import CalendarPage from './pages/CalendarPage';
import DashboardPage from './pages/DashboardPage';
import ItemDetailPage from './pages/ItemDetailPage';
import OutfitDetailPage from './pages/OutfitDetailPage';
import OutfitsPage from './pages/OutfitsPage';
import SuggestionsPage from './pages/SuggestionsPage';
import TryOnPage from './pages/TryOnPage';
import WardrobePage from './pages/WardrobePage';

function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default function App() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/" replace /> : <AuthPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/wardrobe" element={<WardrobePage />} />
        <Route path="/wardrobe/:id" element={<ItemDetailPage />} />
        <Route path="/outfits" element={<OutfitsPage />} />
        <Route path="/outfits/:id" element={<OutfitDetailPage />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/suggestions" element={<SuggestionsPage />} />
        <Route path="/try-on" element={<TryOnPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
