(function installNavigation() {
  if (window.__kissKhTvInstalled) return;
  // A finishing callback from a replaced page can arrive while the new document
  // is still being parsed. Do not mark navigation installed until setup succeeds.
  if (!document.head || !document.body || document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installNavigation, {once: true});
    return;
  }

  var STYLE_ID = '__kisskh_tv_focus_style';
  if (!document.getElementById(STYLE_ID)) {
    var style = document.createElement('style');
    style.id = STYLE_ID;
    style.textContent = `
      .__kisskh_tv_focus {
        outline: 4px solid #ffffff !important;
        outline-offset: 3px !important;
        box-shadow: 0 0 0 2px rgba(0,0,0,.85) !important;
      }
    `;
    document.head.appendChild(style);
  }

  function candidates() {
    var selectors = [
      'a[href]', 'button', 'input:not([type="hidden"])', 'select', 'textarea',
      '[role="button"]', '[tabindex]:not([tabindex="-1"])', 'video'
    ].join(',');
    return Array.prototype.slice.call(document.querySelectorAll(selectors)).filter(function(el) {
      var r = el.getBoundingClientRect();
      var s = window.getComputedStyle(el);
      return r.width > 2 && r.height > 2 && s.visibility !== 'hidden' && s.display !== 'none' && !el.disabled && !el.closest('[inert]');
    });
  }

  function current() {
    return document.querySelector('.__kisskh_tv_focus') || document.activeElement;
  }

  function mark(el) {
    document.querySelectorAll('.__kisskh_tv_focus').forEach(function(x){
      x.classList.remove('__kisskh_tv_focus');
    });
    if (!el) return;
    el.classList.add('__kisskh_tv_focus');
    try { el.focus({preventScroll:true}); } catch(e) { try { el.focus(); } catch(_) {} }
    try { el.scrollIntoView({block:'center', inline:'center', behavior:'smooth'}); } catch(e) {}
  }

  function center(rect) {
    return {x: rect.left + rect.width/2, y: rect.top + rect.height/2};
  }

  window.__kissKhTvMove = function(dir) {
    var list = candidates();
    if (!list.length) {
      window.scrollBy(0, dir === 'up' ? -360 : dir === 'down' ? 360 : 0);
      return;
    }
    var cur = current();
    if (!cur || !list.includes(cur)) {
      mark(list[0]);
      return;
    }
    var cr = cur.getBoundingClientRect(), cc = center(cr), best = null, bestScore = Infinity;
    list.forEach(function(el) {
      if (el === cur) return;
      var r = el.getBoundingClientRect(), c = center(r), dx = c.x-cc.x, dy = c.y-cc.y;
      var valid = (dir==='up' && dy < -6) || (dir==='down' && dy > 6) ||
                  (dir==='left' && dx < -6) || (dir==='right' && dx > 6);
      if (!valid) return;
      var primary = (dir==='up'||dir==='down') ? Math.abs(dy) : Math.abs(dx);
      var secondary = (dir==='up'||dir==='down') ? Math.abs(dx) : Math.abs(dy);
      var score = primary + secondary * 2.4;
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) mark(best);
    else window.scrollBy(
      (dir==='left'?-320:dir==='right'?320:0),
      (dir==='up'?-320:dir==='down'?320:0)
    );
  };

  window.__kissKhTvActivate = function() {
    var el = current();
    if (!el || el === document.body) el = candidates()[0];
    if (!el) return null;
    var rect = el.getBoundingClientRect();
    // Return only the selected control's screen location. Android sends an ordinary
    // touch event, preserving browser user-gesture rules for fullscreen and playback.
    return {x: (rect.left + rect.width / 2) / window.innerWidth,
            y: (rect.top + rect.height / 2) / window.innerHeight};
  };
  window.__kissKhTvInstalled = true;
})();
