// Asks the server who is logged in and fills in the right side of the app bar.
// Sets window.currentUser to the user object, or null if nobody is logged in.
async function loadAuth() {
    const area = document.getElementById('auth-area');

    try {
        const response = await fetch('/api/auth/me');
        window.currentUser = response.ok ? await response.json() : null;
    } catch (err) {
        window.currentUser = null;
    }

    if (!area) return window.currentUser;

    if (window.currentUser) {
        area.innerHTML = '<span class="user-name"></span><button class="btn-flat" id="logout-btn">Log out</button>';
        const label = window.currentUser.name + (window.currentUser.role === 'ADMIN' ? ' (Admin)' : '');
        area.querySelector('.user-name').textContent = label;
        document.getElementById('logout-btn').addEventListener('click', async () => {
            await fetch('/api/auth/logout', { method: 'POST' });
            window.location.href = '/';
        });
    } else {
        area.innerHTML = '<a class="btn-flat" href="/login.html">Log in</a>';
    }
    return window.currentUser;
}