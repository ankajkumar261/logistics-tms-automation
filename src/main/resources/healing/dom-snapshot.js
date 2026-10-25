// Self-healing DOM helper. PlaywrightAdapter inlines this into page.evaluate() calls; it defines H.
//
// It describes elements (tag, role, accessible name, ids, labels, text, position) so the Java side can
// score how similar a candidate is to the element a broken locator used to find.
//
// PRIVACY: it never reads what a user typed (input .value). Snapshots end up in logs, in .healing/ and,
// only if the AI fallback is switched on, in the request sent to the AI advisor.
const H = (() => {
  const MAX_TEXT = 120;
  const norm = s => (s == null ? '' : String(s)).replace(/\s+/g, ' ').trim();
  const cut = (s, n = MAX_TEXT) => { s = norm(s); return s.length > n ? s.slice(0, n) : s; };

  // Framework-generated ids change on every render; they identify nothing.
  const GENERATED_ID = /^(radix-|:r[0-9a-z]*:?|react-|mui-|headlessui-|rc[-_]|ember\d|ext-gen|cdk-)|^[0-9a-f-]{16,}$|\d{5,}/i;
  const stableId = id => (id && !GENERATED_ID.test(id) ? id : '');

  const TEXT_INPUT_TYPES = new Set(['', 'text', 'email', 'tel', 'url', 'password', 'search', 'number',
    'date', 'datetime-local', 'month', 'time', 'week']);
  const BUTTON_INPUT_TYPES = new Set(['button', 'submit', 'reset', 'image']);
  const INTERACTIVE = [
    'a[href]', 'button', 'input', 'select', 'textarea', 'summary', 'label',
    '[role=button]', '[role=link]', '[role=tab]', '[role=menuitem]', '[role=menuitemcheckbox]',
    '[role=menuitemradio]', '[role=option]', '[role=checkbox]', '[role=radio]', '[role=switch]',
    '[role=combobox]', '[role=textbox]', '[role=searchbox]', '[role=treeitem]', '[role=gridcell]',
    '[contenteditable=""]', '[contenteditable=true]', '[onclick]', '[tabindex]:not([tabindex="-1"])'
  ].join(',');
  const LANDMARKS = 'h1,h2,h3,h4,h5,h6,[role=heading],img[alt],[data-testid],[data-test],[data-qa],[data-cy],[id],[aria-label],legend,th';
  const CONTAINERS = 'dialog,[role=dialog],[role=alertdialog],section,form,fieldset,article,aside,main,nav';
  const HEADINGS = 'h1,h2,h3,h4,h5,h6,[role=heading],legend';
  const VOLATILE_DATA = /^data-(state|highlighted|disabled|focus|hover|active|selected|orientation|side|align|radix|value|testid|test|qa|cy|test-id)/;
  const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'OPTION', 'HTML', 'HEAD', 'BODY']);

  function tagOf(el) { return el.tagName.toLowerCase(); }
  function typeOf(el) { return tagOf(el) === 'input' ? (el.getAttribute('type') || '').toLowerCase() : ''; }

  function implicitRole(el) {
    const explicit = el.getAttribute('role');
    if (explicit) return explicit.split(/\s+/)[0].toLowerCase();
    const tag = tagOf(el), type = typeOf(el);
    switch (tag) {
      case 'a': return el.hasAttribute('href') ? 'link' : '';
      case 'button': case 'summary': return 'button';
      case 'select': return el.multiple || el.size > 1 ? 'listbox' : 'combobox';
      case 'textarea': return 'textbox';
      case 'img': return el.getAttribute('alt') === '' ? 'presentation' : 'img';
      case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6': return 'heading';
      case 'nav': return 'navigation';
      case 'dialog': return 'dialog';
      case 'li': return 'listitem';
      case 'table': return 'table';
      case 'tr': return 'row';
      case 'td': return 'cell';
      case 'th': return 'columnheader';
      case 'input':
        if (BUTTON_INPUT_TYPES.has(type)) return 'button';
        if (type === 'checkbox') return 'checkbox';
        if (type === 'radio') return 'radio';
        if (type === 'range') return 'slider';
        if (type === 'number') return 'spinbutton';
        if (type === 'file' || type === 'hidden' || type === 'color') return '';
        if (el.hasAttribute('list')) return 'combobox';
        return type === 'search' ? 'searchbox' : 'textbox';
    }
    return '';
  }

  // Visible text of a node, leaving out the contents of nested form controls (a <label> wrapping a
  // <select> would otherwise "say" every option).
  function ownText(node) {
    const parts = [];
    const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT, {
      acceptNode: t => (t.parentElement && t.parentElement.closest('select,textarea,option,script,style')
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT)
    });
    while (walker.nextNode()) parts.push(walker.currentNode.textContent);
    return cut(parts.join(' '));
  }

  function labelText(el) {
    const parts = [];
    const by = el.getAttribute('aria-labelledby');
    if (by) by.split(/\s+/).forEach(id => { const l = document.getElementById(id); if (l) parts.push(ownText(l)); });
    if (el.labels) for (const l of el.labels) parts.push(ownText(l));
    return cut(parts.join(' '));
  }

  function isFormControl(el) {
    const tag = tagOf(el);
    return (tag === 'input' && !BUTTON_INPUT_TYPES.has(typeOf(el))) || tag === 'select' || tag === 'textarea';
  }

  function accessibleName(el, role) {
    const by = el.getAttribute('aria-labelledby');
    if (by) {
      const t = cut(by.split(/\s+/).map(id => document.getElementById(id)).filter(Boolean).map(ownText).join(' '));
      if (t) return t;
    }
    const aria = cut(el.getAttribute('aria-label'));
    if (aria) return aria;
    if (isFormControl(el)) {
      return labelText(el) || cut(el.getAttribute('title')) || cut(el.getAttribute('placeholder'));
    }
    if (tagOf(el) === 'input' && BUTTON_INPUT_TYPES.has(typeOf(el))) {
      // A button-type input's value is its caption, not user data.
      return cut(el.getAttribute('value')) || cut(el.getAttribute('title'));
    }
    if (tagOf(el) === 'img') return cut(el.getAttribute('alt')) || cut(el.getAttribute('title'));
    const nameFromContent = ['button', 'link', 'heading', 'tab', 'menuitem', 'menuitemcheckbox', 'menuitemradio',
      'option', 'checkbox', 'radio', 'switch', 'treeitem', 'cell', 'gridcell', 'columnheader', 'rowheader', 'row',
      'listitem', 'tooltip', 'combobox'];
    if (nameFromContent.includes(role) || tagOf(el) === 'label') {
      return ownText(el) || cut(el.getAttribute('title'));
    }
    return cut(el.getAttribute('title'));
  }

  function isVisible(el) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return false;
    if (el.checkVisibility) return el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
    const s = getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none';
  }

  function isEnabled(el) {
    if (el.disabled) return false;
    if (el.closest('[aria-disabled=true]')) return false;
    const fs = el.closest('fieldset[disabled]');
    return !(fs && !fs.querySelector('legend')?.contains(el));
  }

  function isEditable(el) {
    if (!isEnabled(el)) return false;
    if (el.isContentEditable) return true;
    return !el.readOnly && el.getAttribute('aria-readonly') !== 'true';
  }

  function testIdOf(el) {
    for (const a of ['data-testid', 'data-test-id', 'data-test', 'data-qa', 'data-cy']) {
      const v = el.getAttribute(a);
      if (v) return [a, v];
    }
    return ['', ''];
  }

  function ancestry(el, depth = 5) {
    const out = [];
    for (let p = el.parentElement; p && p !== document.body && out.length < depth; p = p.parentElement) {
      const role = p.getAttribute('role');
      out.push(tagOf(p) + (role ? '[role=' + role + ']' : ''));
    }
    return out;
  }

  function nearestHeading(el) {
    for (let c = el.parentElement && el.parentElement.closest(CONTAINERS); c; c = c.parentElement && c.parentElement.closest(CONTAINERS)) {
      const h = c.querySelector(HEADINGS);
      if (h && h !== el) return cut(h.innerText || h.textContent, 80);
    }
    return '';
  }

  function cssPath(el) {
    const segs = [];
    for (let n = el; n && n.nodeType === 1 && n !== document.documentElement; n = n.parentElement) {
      const id = stableId(n.id);
      if (id && n !== el) { segs.unshift('[id="' + id.replace(/"/g, '\\"') + '"]'); break; }
      const tag = tagOf(n);
      let i = 1;
      for (let s = n.previousElementSibling; s; s = s.previousElementSibling) if (s.tagName === n.tagName) i++;
      segs.unshift(tag + ':nth-of-type(' + i + ')');
      if (tag === 'body') break;
    }
    return segs.join(' > ');
  }

  function dataAttrs(el) {
    const out = {};
    let n = 0;
    for (const a of el.attributes) {
      if (!a.name.startsWith('data-') || VOLATILE_DATA.test(a.name)) continue;
      out[a.name] = cut(a.value, 60);
      if (++n >= 6) break;
    }
    return out;
  }

  function snapshot(el) {
    const role = implicitRole(el);
    const tag = tagOf(el);
    const [testIdAttr, testId] = testIdOf(el);
    const r = el.getBoundingClientRect();
    const href = tag === 'a' && el.getAttribute('href') ? el.getAttribute('href') : '';
    return {
      tag, type: typeOf(el), id: stableId(el.id), name: cut(el.getAttribute('name'), 80),
      testId, testIdAttr, href: cut(href.replace(/^https?:\/\/[^/]+/, ''), 120),
      role, accessibleName: accessibleName(el, role), ariaLabel: cut(el.getAttribute('aria-label')),
      labelText: isFormControl(el) ? labelText(el) : '',
      text: isFormControl(el) ? '' : ownText(el),
      placeholder: cut(el.getAttribute('placeholder')), title: cut(el.getAttribute('title')),
      alt: cut(el.getAttribute('alt')), dataAttrs: dataAttrs(el),
      ancestry: ancestry(el), heading: nearestHeading(el),
      inDialog: !!el.closest('dialog,[role=dialog],[role=alertdialog]'),
      x: Math.round(r.left + window.scrollX), y: Math.round(r.top + window.scrollY),
      w: Math.round(r.width), h: Math.round(r.height),
      visible: isVisible(el), enabled: isEnabled(el), editable: isEditable(el),
      cssPath: cssPath(el)
    };
  }

  // Can {action} be performed on this element at all? Healing never swaps in an element the action
  // can't apply to (e.g. a fill() never lands on a checkbox).
  function compatible(el, action) {
    const tag = tagOf(el), type = typeOf(el), role = implicitRole(el);
    const textEntry = (tag === 'input' && TEXT_INPUT_TYPES.has(type)) || tag === 'textarea' || el.isContentEditable;
    switch (action) {
      case 'FILL': case 'TYPE': return textEntry;
      case 'PRESS': return textEntry || tag === 'button' || tag === 'a' || tag === 'select' || el.hasAttribute('tabindex');
      case 'CHECK': return (tag === 'input' && (type === 'checkbox' || type === 'radio'))
        || ['checkbox', 'radio', 'switch', 'menuitemcheckbox', 'menuitemradio'].includes(role);
      case 'SELECT': return tag === 'select';
      case 'UPLOAD': return tag === 'input' && type === 'file';
      default: return true; // CLICK, HOVER, WAIT, READ
    }
  }

  function hasOwnText(el) {
    for (const n of el.childNodes) if (n.nodeType === 3 && n.textContent.trim()) return true;
    return false;
  }

  // Candidate elements under {roots} that {opts.action} could act on. Also remembers them on
  // window.__healCandidates so a synthesized locator can be checked against the exact element.
  function collect(roots, opts) {
    const action = opts.action;
    const max = opts.max || 800;
    const broad = ['CLICK', 'HOVER', 'WAIT', 'READ'].includes(action);
    const requireVisible = action !== 'UPLOAD';
    const picked = new Set();
    const els = [];
    const rootSet = new Set(roots);
    const add = el => {
      if (els.length >= max || picked.has(el) || rootSet.has(el) || SKIP_TAGS.has(el.tagName)) return;
      if (!compatible(el, action)) return;
      if (requireVisible && !isVisible(el)) return;
      picked.add(el);
      els.push(el);
    };
    for (const root of roots) root.querySelectorAll(INTERACTIVE).forEach(add);
    if (broad) {
      for (const root of roots) root.querySelectorAll(LANDMARKS).forEach(add);
      // Text leaves (getByText targets), unless they sit inside an interactive element already picked.
      for (const root of roots) {
        for (const el of root.querySelectorAll('*')) {
          if (els.length >= max) break;
          if (!hasOwnText(el) || el.closest('select')) continue;
          const owner = el.parentElement && el.parentElement.closest(INTERACTIVE);
          if (owner && picked.has(owner)) continue;
          add(el);
        }
      }
    }
    window.__healCandidates = els;
    const index = new Map(els.map((el, i) => [el, i]));
    return els.map((el, i) => {
      const s = snapshot(el);
      s.index = i;
      s.ancestorIndex = -1;
      for (let p = el.parentElement; p; p = p.parentElement) {
        if (index.has(p)) { s.ancestorIndex = index.get(p); break; }
        if (rootSet.has(p)) break;
      }
      return s;
    });
  }

  return { snapshot, collect };
})();
