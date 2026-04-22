// pages/index.js
function render(data) { return h("view", { "class": "page-index" }, h("view", { "class": "container" }, h("text", { "class": "title" }, "" + data.title + ""), h("text", { "class": "sub-title" }, "Tap count: " + data.tapCount + ""), (data.showList) ? h("view", { "class": "list-panel" }, (data.list || []).map((item, idx) => h("view", { "class": "list-item", "bindtap": "onItemTap", "data-v-id": item.id, "data-v-index": idx, key: item.id }, h("text", { "class": "item-text" }, "" + item.name + "")))) : null, h("button", { "bindtap": "onAddItem", "class": "cta success" }, "+ Add Item"), h("button", { "bindtap": "onJumpPage", "class": "cta primary" }, "跳转到第二页"))); }
console.log('[mini] app-service.js loaded');
Page(Object.assign({ render }, {
  data: {
    title: 'Hello Mini Framework',
    showList: true,
    tapCount: 0,
    lastTappedId: '',
    lastTappedIndex: '',
    list: [
      { id: 1, name: 'Phase 1: Bridge & Dual Thread' },
      { id: 2, name: 'Phase 2: VNode & Components' },
      { id: 3, name: 'Phase 3: DSL Compiler' },
      { id: 4, name: 'Phase 4: Native Renderer' },
      { id: 5, name: 'Phase 5: Platform APIs' }
    ]
  },

  onItemTap(event) {
    const data = this.data;
    const next = data.tapCount + 1;
    const id = event && event.dataset ? event.dataset.id : '';
    const index = event && event.dataset ? event.dataset.index : '';
    this.setData({
      tapCount: next,
      title: 'Tapped item #' + id + ' (index ' + index + ')',
      lastTappedId: id,
      lastTappedIndex: index
    });

    // 跨页可观测状态：供 second 页 onLoad 读取
    globalThis.__miniLastTap__ = {
      id: id,
      index: index,
      tapCount: next,
      title: 'Tapped item #' + id + ' (index ' + index + ')'
    };

    console.log('[index] item tapped id=' + id + ' index=' + index);
  },

  onAddItem() {
    const data = this.data;
    const newId = data.list.length + 1;
    const nextList = data.list.concat([{ id: newId, name: 'New Item #' + newId }]);
    this.setData({ list: nextList });
  },

  onJumpPage() {
    if (typeof mini !== 'undefined' && mini.callAPI) {
      mini.callAPI('navigation', 'navigateTo', { page: 'app/pages/second.js' });
    }
  }
}));

