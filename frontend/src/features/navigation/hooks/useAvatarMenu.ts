import { useState } from "react";

export function useAvatarMenu() {
    const [showLogoutModal, setShowLogoutModal] = useState(false);

    const openLogoutModal = () => setShowLogoutModal(true);
    const closeLogoutModal = () => setShowLogoutModal(false);

    return {
        showLogoutModal,
        openLogoutModal,
        closeLogoutModal,
    };
}
