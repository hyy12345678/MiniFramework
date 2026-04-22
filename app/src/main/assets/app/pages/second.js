// pages/second.js
function render(data) { return h("view", { "class": "page-second container" }, h("text", { "class": "title" }, "" + data.title + ""), h("text", { "class": "desc" }, "" + data.desc + ""), h("view", { "class": "from-index-panel" }, h("text", { "class": "from-index-title" }, "来自 index 的最近点击:"), h("text", { "class": "from-index-content" }, "" + data.fromIndexTitle + ""), h("text", { "class": "from-index-content" }, "Tap count: " + data.fromIndexTapCount + "")), h("button", { "bindtap": "onBack", "class": "back-btn" }, "返回首页")); }
Page(Object.assign({ render }, {
  data: {
    title: 'Second Page',
    desc: '你已跳转到第二个页面！',
    fromIndexTitle: '（暂无点击记录）',
    fromIndexTapCount: 0
  },

  onLoad() {
    const lastTap = globalThis.__miniLastTap__;
    if (lastTap) {
      this.setData({
        fromIndexTitle: lastTap.title,
        fromIndexTapCount: lastTap.tapCount
      });
      console.log('[second] restore last tap', JSON.stringify(lastTap));
    } else {
      console.log('[second] no last tap found');
    }
  },

  onBack() {
    if (typeof mini !== 'undefined' && mini.callAPI) {
      mini.callAPI('navigation', 'navigateTo', { page: 'app/pages/index.js' });
    }
  }
}));

