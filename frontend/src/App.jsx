import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth";
import { ToastProvider } from "./toast";
import Layout from "./components/Layout";
import RequireAuth from "./components/RequireAuth";
import AuctionDetailPage from "./pages/AuctionDetailPage";
import AuctionsPage from "./pages/AuctionsPage";
import CollectionDetailPage from "./pages/CollectionDetailPage";
import CollectionsPage from "./pages/CollectionsPage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import MyBidsPage from "./pages/MyBidsPage";
import MyCollectionsPage from "./pages/MyCollectionsPage";
import MyLotsPage from "./pages/MyLotsPage";
import NewLotPage from "./pages/NewLotPage";
import PayPage from "./pages/PayPage";
import PaymentsPage from "./pages/PaymentsPage";
import RegisterPage from "./pages/RegisterPage";

export default function App() {
  return (
    <ToastProvider>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/auctions" element={<AuctionsPage />} />
            <Route path="/auctions/live" element={<AuctionsPage filter="LIVE" />} />
            <Route path="/auctions/upcoming" element={<AuctionsPage filter="SCHEDULED" />} />
            <Route path="/collections" element={<CollectionsPage />} />
            <Route path="/collections/:id" element={<CollectionDetailPage />} />
            <Route path="/auctions/new" element={<RequireAuth><NewLotPage /></RequireAuth>} />
            <Route path="/auctions/:id" element={<AuctionDetailPage />} />
            <Route path="/auctions/:id/pay" element={<RequireAuth><PayPage /></RequireAuth>} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/my-bids" element={<RequireAuth><MyBidsPage /></RequireAuth>} />
            <Route path="/my-lots" element={<RequireAuth><MyLotsPage /></RequireAuth>} />
            <Route path="/my-collections" element={<RequireAuth><MyCollectionsPage /></RequireAuth>} />
            <Route path="/payments" element={<RequireAuth><PaymentsPage /></RequireAuth>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
    </ToastProvider>
  );
}
