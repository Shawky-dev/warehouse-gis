import { useNavigate } from "react-router-dom";
import { AuthLoginForm } from "@/features/auth/shared/components/AuthLoginForm";
import { useAuth } from "@/features/auth/context/AuthContext";
import { PATHS } from "@/shared/consts/paths";
import { useI18n } from "@/i18n";

const LandlordLoginPage = () => {
  const navigate = useNavigate();
  const { t } = useI18n();
  const { login } = useAuth();

  const handleSubmit = async (payload: { email: string; password: string }) => {
    await login(payload);
    navigate(PATHS.LANDLORD.ROOT, { replace: true });
  };

  return (
    <AuthLoginForm
      title={t("login.landlordTitle")}
      description={t("login.landlordDescription")}
      placeholderEmail="admin@system.local"
      onSubmit={handleSubmit}
    />
  );
};

export default LandlordLoginPage;
