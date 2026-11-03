import Cookies from 'js-cookie';

export const getCsrfToken = () => Cookies.get('XSRF-TOKEN');

export async function refreshCsrfToken() {
    const response = await fetch('/', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('Could not refresh upload token');
}

export async function fetchWithCsrf(url, options) {
    const send = () => fetch(url, {
        ...options,
        credentials: 'same-origin',
        headers: { ...options.headers, 'X-XSRF-TOKEN': getCsrfToken() }
    });

    let response = await send();
    if (response.status === 403) {
        await refreshCsrfToken();
        response = await send();
    }
    return response;
}
