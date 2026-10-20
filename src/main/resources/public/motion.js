// Material "ripple": a circle that spreads from the point you press and fades out
document.addEventListener('pointerdown', function (event) {
    const target = event.target.closest('.btn-flat, .btn-raised, .fab, .vote-btn, .icon-btn, .tab');
    if (!target) return;

    const rect = target.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height) * 2;

    const ripple = document.createElement('span');
    ripple.className = 'ripple';
    ripple.style.width = size + 'px';
    ripple.style.height = size + 'px';
    ripple.style.left = (event.clientX - rect.left - size / 2) + 'px';
    ripple.style.top = (event.clientY - rect.top - size / 2) + 'px';

    target.appendChild(ripple);
    ripple.addEventListener('animationend', function () { ripple.remove(); });
});