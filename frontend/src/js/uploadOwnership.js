const STORAGE_PREFIX = 'speechdrop:uploads:';
const SERVER_PURGE_INTERVAL_SECONDS = 5184000; // 60 days, matching config.example.json.
const RETENTION_SECONDS = SERVER_PURGE_INTERVAL_SECONDS + 24 * 60 * 60;

// Browser-local UI ownership, not server authorization.
export const createUploadOwnershipStore = roomId => {
    const roomPrefix = `${STORAGE_PREFIX}${roomId}:`;
    let owned = {};

    const read = () => {
        try {
            const stored = {};
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith(roomPrefix)) stored[key.slice(roomPrefix.length)] = true;
            }
            owned = stored;
        } catch (_) {
            // Preserve current-tab ownership when browser storage is unavailable.
        }
        return { ...owned };
    };

    const cleanup = now => {
        const cutoff = now - RETENTION_SECONDS;
        for (let i = localStorage.length - 1; i >= 0; i--) {
            const key = localStorage.key(i);
            if (key && key.startsWith(STORAGE_PREFIX)) {
                const addedAt = Number(localStorage.getItem(key));
                if (Number.isFinite(addedAt) && addedAt < cutoff) localStorage.removeItem(key);
            }
        }
    };

    return {
        read,
        add(index) {
            owned[index] = true;
            try {
                const now = Math.floor(Date.now() / 1000);
                cleanup(now);
                localStorage.setItem(roomPrefix + index, String(now));
                return read();
            } catch (_) {
                return { ...owned };
            }
        },
        remove(index) {
            delete owned[index];
            try {
                localStorage.removeItem(roomPrefix + index);
            } catch (_) {
                // Browser storage is optional.
            }
            return { ...owned };
        },
        handlesStorageEvent(event) {
            return event.key === null || event.key.startsWith(roomPrefix);
        }
    };
};
