// ============================================================
// Demo Page — simulates a mini-program page
// This runs in the V8 engine thread, NOT in WebView.
// ============================================================

MiniFramework.Page({
  // ---- Page data ----
  data: {
    title: 'Hello Mini Framework',
    items: [
      { id: 1, name: 'Phase 1: Bridge & Dual Thread' },
      { id: 2, name: 'Phase 2: VNode & Components' },
      { id: 3, name: 'Phase 3: DSL Compiler' },
      { id: 4, name: 'Phase 4: Native Renderer' },
      { id: 5, name: 'Phase 5: Platform APIs' }
    ],
    tapCount: 0
  },

  // ---- Lifecycle ----
  onLoad: function(options) {
    console.log('[demo] onLoad called');
  },

  onReady: function() {
    console.log('[demo] onReady called');
  },

  // ---- Render function (Phase 2: returns VNode tree) ----
  render: function(data) {
    return h('div', {style: 'padding: 16px'},
      h('h1', {style: 'font-size: 22px; margin-bottom: 12px'}, data.title),
      h('p', {style: 'color: #666; margin-bottom: 16px'}, 'Tap count: ' + data.tapCount),
      h('ul', {style: 'list-style: none; padding: 0'},
        data.items.map(function(item, i) {
          return h('li', {
            bindtap: 'onItemTap',
            'data-v-id': String(item.id),
            'data-v-index': String(i),
            style: 'padding: 14px 16px; border-bottom: 1px solid #eee; cursor: pointer;',
            key: item.id
          },
            h('span', {style: 'color: #07c160; margin-right: 8px'}, '\u25b8'),
            item.name
          );
        })
      ),
      h('div', {
        bindtap: 'onAddItem',
        style: 'margin-top: 16px; padding: 12px; background: #07c160; color: white; text-align: center; border-radius: 8px; cursor: pointer;'
      }, '+ Add Item'),
      h('div', {
        bindtap: 'onJumpPage',
        style: 'margin-top: 16px; padding: 12px; background: #1989fa; color: white; text-align: center; border-radius: 8px; cursor: pointer;'
      }, '跳转到第二页')
    );
  },

  // ---- Event handlers ----
  onItemTap: function(event) {
    console.log('[demo] Item tapped! id=' + event.dataset.id + ' index=' + event.dataset.index);
    var data = MiniFramework.getData();
    MiniFramework.setData({
      tapCount: data.tapCount + 1,
      title: 'Tapped item #' + event.dataset.id
    });
  },

  onAddItem: function(event) {
    console.log('[demo] Add item tapped');
    var data = MiniFramework.getData();
    var newId = data.items.length + 1;
    var items = data.items.slice();
    items.push({ id: newId, name: 'New Item #' + newId });
    MiniFramework.setData({ items: items });
  },

  onJumpPage: function() {
    if (typeof mini !== 'undefined' && mini.callAPI) {
      mini.callAPI('navigation', 'navigateTo', { page: 'pages/second.js' });
    } else {
      console.warn('mini.callAPI not found');
    }
  }
});
