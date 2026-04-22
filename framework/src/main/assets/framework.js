// ============================================================
// Mini Framework — JS-side runtime (loaded into V8 engine)
// ============================================================

(function() {
  'use strict';

  // ---- Bridge communication ----

  /**
   * Send a message from JS to Native.
   * __bridgePostMessage__ is injected by V8Engine.registerNativeFunction.
   */
  function postToNative(message) {
    if (typeof __bridgePostMessage__ === 'function') {
      __bridgePostMessage__(JSON.stringify(message));
    } else {
      console.error('[framework] __bridgePostMessage__ not available');
    }
  }

  // Pending callbacks for request-response pattern
  var pendingCallbacks = {};

  /**
   * Called by Native to deliver messages to JS.
   * Registered as a global function by V8Engine.
   */
  function __onMessage__(json) {
    try {
      var msg = JSON.parse(json);
    } catch (e) {
      console.error('[framework] Failed to parse message:', json);
      return;
    }

    if (msg.type === 'response' && msg.callbackId && pendingCallbacks[msg.callbackId]) {
      var cb = pendingCallbacks[msg.callbackId];
      delete pendingCallbacks[msg.callbackId];
      if (msg.errorCode && msg.errorCode !== 0) {
        cb.fail && cb.fail(msg);
      } else {
        cb.success && cb.success(msg.data);
      }
      return;
    }

    if (msg.type === 'event') {
      handleNativeEvent(msg);
      return;
    }
  }

  // Expose to global scope so Native can call it
  this.__onMessage__ = __onMessage__;

  // ---- Native API calls ----

  function callNativeAPI(module, method, data, callbacks) {
    if (module === 'render' && method === 'renderHTML') {
      console.log('[mini] callNativeAPI renderHTML', data && data.html);
    }
    var id = generateId();
    var message = {
      id: id,
      type: 'request',
      module: module,
      method: method,
      data: data,
      callbackId: id,
      errorCode: 0,
      errorMsg: null
    };

    if (callbacks) {
      pendingCallbacks[id] = callbacks;
    }
    postToNative(message);
  }

  // ---- VNode ----

  var SELF_CLOSING = {br:1,hr:1,img:1,input:1,meta:1,link:1,area:1,col:1};

  /**
   * Create a virtual DOM node.
   * Usage: h('div', {style: '...'}, 'text', h('span', null, 'child'))
   */
  function h(type, props) {
    var children = [];
    for (var i = 2; i < arguments.length; i++) {
      var child = arguments[i];
      if (child == null || child === false) continue;
      if (typeof child === 'string' || typeof child === 'number') {
        children.push({type: '__TEXT__', props: {}, children: [], text: String(child), key: null});
      } else if (Array.isArray(child)) {
        for (var j = 0; j < child.length; j++) {
          var c = child[j];
          if (c == null || c === false) continue;
          if (typeof c === 'string' || typeof c === 'number') {
            children.push({type: '__TEXT__', props: {}, children: [], text: String(c), key: null});
          } else {
            children.push(c);
          }
        }
      } else {
        children.push(child);
      }
    }

    var key = props && props.key != null ? props.key : null;
    var cleanProps = {};
    if (props) {
      for (var k in props) {
        if (k !== 'key' && props.hasOwnProperty(k)) {
          cleanProps[k] = props[k];
        }
      }
    }

    return {type: type, props: cleanProps, children: children, text: null, key: key};
  }

  /**
   * Convert VNode tree to HTML string (used for initial render).
   * Generates HTML with no whitespace between tags so that
   * DOM childNodes indices match VNode children indices exactly.
   */
  function vnodeToHTML(vnode) {
    console.log('[mini] vnodeToHTML input', JSON.stringify(vnode));
    if (vnode.type === '__TEXT__') {
      console.log('[mini] vnodeToHTML text', vnode.text);
      return escapeHTML(vnode.text);
    }
    var html = '<' + vnode.type;
    console.log('[mini] vnodeToHTML tag', vnode.type, vnode.props);
    var props = vnode.props;
    for (var key in props) {
      if (!props.hasOwnProperty(key)) continue;
      var val = props[key];
      if (val === false || val == null) continue;
      if (key === 'bindtap' || key === 'bindlongtap') {
        html += ' data-' + key + '="' + escapeAttr(val) + '"';
      } else {
        html += ' ' + key + '="' + escapeAttr(String(val)) + '"';
      }
    }
    if (SELF_CLOSING[vnode.type]) return html + '/>';
    html += '>';
    for (var i = 0; i < vnode.children.length; i++) {
      html += vnodeToHTML(vnode.children[i]);
    }
    html += '</' + vnode.type + '>';
    return html;
  }

  function escapeHTML(s) {
    if (typeof s !== 'string') s = String(s);
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ---- Diff Algorithm ----

  var PATCH_REPLACE = 1;
  var PATCH_PROPS   = 2;
  var PATCH_TEXT    = 3;
  var PATCH_INSERT  = 4;
  var PATCH_REMOVE  = 5;

  function diff(oldVNode, newVNode) {
    var patches = [];
    diffNode(oldVNode, newVNode, [], patches);
    return patches;
  }

  function diffNode(oldNode, newNode, path, patches) {
    if (oldNode === newNode) return;

    // Text nodes
    if (oldNode.type === '__TEXT__' && newNode.type === '__TEXT__') {
      if (oldNode.text !== newNode.text) {
        patches.push({t: PATCH_TEXT, p: path, text: newNode.text});
      }
      return;
    }

    // Different type — full replace
    if (oldNode.type !== newNode.type) {
      patches.push({t: PATCH_REPLACE, p: path, vnode: newNode});
      return;
    }

    // Same element type — diff props
    var propPatch = diffProps(oldNode.props, newNode.props);
    if (propPatch) {
      patches.push({t: PATCH_PROPS, p: path, props: propPatch});
    }

    // Diff children
    diffChildren(oldNode.children, newNode.children, path, patches);
  }

  function diffProps(oldProps, newProps) {
    var patch = null;
    for (var key in newProps) {
      if (newProps[key] !== oldProps[key]) {
        if (!patch) patch = {};
        patch[key] = newProps[key];
      }
    }
    for (var key in oldProps) {
      if (!(key in newProps)) {
        if (!patch) patch = {};
        patch[key] = null;
      }
    }
    return patch;
  }

  function diffChildren(oldChildren, newChildren, parentPath, patches) {
    var oldLen = oldChildren.length;
    var newLen = newChildren.length;
    var minLen = Math.min(oldLen, newLen);

    // Diff common positions
    for (var i = 0; i < minLen; i++) {
      diffNode(oldChildren[i], newChildren[i], parentPath.concat(i), patches);
    }

    // New children appended
    for (var i = oldLen; i < newLen; i++) {
      patches.push({t: PATCH_INSERT, p: parentPath, index: i, vnode: newChildren[i]});
    }

    // Old children removed (high to low to keep indices stable during apply)
    for (var i = oldLen - 1; i >= newLen; i--) {
      patches.push({t: PATCH_REMOVE, p: parentPath, index: i});
    }
  }

  // ---- Page / Component model ----

  var currentPageData = {};
  var currentPageConfig = null;
  var eventHandlers = {};
  var currentVNode = null;

  var MiniFramework = {
    /**
     * Register a page.
     */
    Page: function(config) {
      currentPageConfig = config;
      currentPageData = config.data || {};
      currentVNode = null;
      eventHandlers = config.methods || {};

      // 兼容小程序 Page 实例写法：this.setData()/this.getData()
      config.setData = function(partialData) {
        MiniFramework.setData(partialData);
      };
      config.getData = function() {
        return MiniFramework.getData();
      };

      // Also collect top-level functions as event handlers
      for (var key in config) {
        if (typeof config[key] === 'function' && key !== 'onLoad' && key !== 'onShow'
            && key !== 'onReady' && key !== 'onHide' && key !== 'onUnload') {
          eventHandlers[key] = config[key];
        }
      }

      // Trigger onLoad lifecycle
      if (typeof config.onLoad === 'function') {
        config.onLoad({});
      }

      // Initial render
      this.render();

      // Trigger onReady
      if (typeof config.onReady === 'function') {
        config.onReady();
      }
    },

    /**
     * Update page data and re-render.
     */
    setData: function(partialData) {
      for (var key in partialData) {
        if (partialData.hasOwnProperty(key)) {
          currentPageData[key] = partialData[key];
        }
      }
      this.render();
    },

    /**
     * Render the current page.
     * - render() may return a VNode tree (Phase 2) or an HTML string (Phase 1 fallback).
     * - First VNode render: convert to HTML and send via renderHTML.
     * - Subsequent VNode renders: diff and send patches.
     */
    render: function() {
      if (!currentPageConfig || typeof currentPageConfig.render !== 'function') {
        callNativeAPI('render', 'renderHTML', {
          html: '<div style="padding:20px;color:#999;">No render function defined</div>'
        });
        return;
      }

      var result = currentPageConfig.render(currentPageData);

      // Phase 1 fallback: render() returns an HTML string
      if (typeof result === 'string') {
        callNativeAPI('render', 'renderHTML', { html: result });
        currentVNode = null;
        return;
      }

      // Phase 2: render() returns a VNode tree
      var newVNode = result;

      if (currentVNode === null) {
        // First render — convert VNode to HTML for full DOM creation
        var html = vnodeToHTML(newVNode);
        callNativeAPI('render', 'renderHTML', { html: html });
      } else {
        // Subsequent renders — diff and patch
        var patches = diff(currentVNode, newVNode);
        if (patches.length > 0) {
          callNativeAPI('render', 'applyPatches', { patches: JSON.stringify(patches) });
        }
      }

      currentVNode = newVNode;
    },

    /**
     * Get current page data.
     */
    getData: function() {
      return currentPageData;
    }
  };

  // ---- Event handling ----

  /**
   * Handle events coming from Native (e.g., user taps in WebView).
   */
  function handleNativeEvent(msg) {
    if (msg.module === 'ui' && msg.method === 'event') {
      var eventData = msg.data;
      if (eventData && eventData.handlerName) {
        var handler = eventHandlers[eventData.handlerName];
        if (typeof handler === 'function') {
          handler.call(currentPageConfig, eventData);
        } else {
          console.warn('[framework] No handler for event: ' + eventData.handlerName);
        }
      }
    }
  }

  // ---- Utilities ----

  var idCounter = 0;
  function generateId() {
    return 'msg_' + (++idCounter) + '_' + Date.now();
  }

  // ---- Expose globals ----

  this.MiniFramework = MiniFramework;
  this.Page = function(config) {
    return MiniFramework.Page(config);
  };
  this.getCurrentPages = function() {
    return currentPageConfig ? [currentPageConfig] : [];
  };
  this.h = h;
  this.mini = {
    callAPI: callNativeAPI
  };

}).call(this);
