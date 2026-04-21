// pages/second.js
MiniFramework.Page({
    data: {},
    render: function() {
        return h('div', {style: 'padding: 24px;'},
            h('h2', null, 'Second Page'),
            h('p', null, '你已跳转到第二个页面！'),
            h('div', {
                bindtap: 'onBack',
                style: 'margin-top: 24px; padding: 12px; background: #07c160; color: white; text-align: center; border-radius: 8px; cursor: pointer;'
            }, '返回首页')
        );
    },
    onBack: function() {
        if (typeof mini !== 'undefined' && mini.callAPI) {
            mini.callAPI('navigation', 'navigateTo', { page: 'pages/demo.js' });
        }
    }
});
