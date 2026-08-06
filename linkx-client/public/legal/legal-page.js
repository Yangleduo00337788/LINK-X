/* global window, document */
(function () {
  'use strict'

  var tocState = {
    items: [],
    sidebar: null,
    list: null
  }

  function getDocKind() {
    return document.body.getAttribute('data-doc') || 'service'
  }

  function getLang() {
    var params = new URLSearchParams(window.location.search)
    var lang = params.get('lang')
    return lang === 'en-US' ? 'en-US' : 'zh-CN'
  }

  function docKey(doc, lang) {
    return doc + '.' + lang
  }

  function isMobileToc() {
    return window.matchMedia('(max-width: 960px)').matches
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script')
      script.src = src
      script.async = true
      script.onload = function () { resolve() }
      script.onerror = function () { reject(new Error('Failed to load ' + src)) }
      document.head.appendChild(script)
    })
  }

  function loadDocData(doc, lang) {
    var key = docKey(doc, lang)
    var docs = window.__LEGAL_DOCS__
    if (docs && docs[key]) return Promise.resolve(docs[key])
    return loadScript('./data/' + key + '.js').then(function () {
      return window.__LEGAL_DOCS__[key]
    })
  }

  function renderBlock(block) {
    if (!block || !block.type) return null
    if (block.type === 'p') {
      var p = document.createElement('p')
      if (block.html) p.innerHTML = block.html
      else p.textContent = block.text || ''
      return p
    }
    if (block.type === 'ul') {
      var ul = document.createElement('ul')
      ;(block.items || []).forEach(function (item) {
        var li = document.createElement('li')
        li.textContent = item
        ul.appendChild(li)
      })
      return ul
    }
    return null
  }

  function renderLangSwitch(currentLang) {
    var bar = document.getElementById('doc-topbar')
    if (!bar) return
    bar.innerHTML =
      '<div class="doc-topbar-inner"><div class="lang-switch">' +
      '<button type="button" data-lang="zh-CN"' + (currentLang === 'zh-CN' ? ' class="active"' : '') + '>中文</button>' +
      '<span class="lang-sep">|</span>' +
      '<button type="button" data-lang="en-US"' + (currentLang === 'en-US' ? ' class="active"' : '') + '>English</button>' +
      '</div></div>'
    bar.querySelectorAll('[data-lang]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var next = btn.getAttribute('data-lang')
        if (next === currentLang) return
        var url = new URL(window.location.href)
        url.searchParams.set('lang', next)
        window.location.href = url.href
      })
    })
  }

  function scrollToSection(id) {
    var head = document.querySelector('#' + id + ' .section-head')
    if (head) head.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function renderDoc(doc, lang) {
    document.title = doc.title
    document.documentElement.lang = lang === 'en-US' ? 'en' : 'zh-CN'

    var main = document.getElementById('doc-main')
    var toc = document.getElementById('doc-toc')
    if (!main || !toc) return

    main.innerHTML = ''
    toc.innerHTML = ''

    var title = document.createElement('h1')
    title.className = 'doc-title'
    title.textContent = doc.title
    main.appendChild(title)

    var list = document.createElement('ul')
    list.className = 'toc-list'
    toc.appendChild(list)

    var items = []

    doc.sections.forEach(function (section) {
      var sec = document.createElement('section')
      sec.className = 'section'
      sec.id = section.id

      var headClass = section.intro ? 'section-head intro' : 'section-head'
      var h2 = document.createElement('h2')
      h2.className = headClass
      h2.innerHTML = '<span class="bar"></span>' + section.title
      sec.appendChild(h2)

      ;(section.content || []).forEach(function (block) {
        var el = renderBlock(block)
        if (el) sec.appendChild(el)
      })
      main.appendChild(sec)

      var li = document.createElement('li')
      li.className = 'toc-item'
      li.setAttribute('data-target', section.id)
      var a = document.createElement('a')
      a.href = '#' + section.id
      a.textContent = section.title
      a.addEventListener('click', function (e) {
        e.preventDefault()
        scrollToSection(section.id)
      })
      li.appendChild(a)
      list.appendChild(li)

      items.push({ id: section.id, head: h2, item: li })
    })

    var footer = document.createElement('footer')
    footer.className = 'footer'
    ;(doc.footer && doc.footer.lines || []).forEach(function (line) {
      var fp = document.createElement('p')
      fp.textContent = line
      footer.appendChild(fp)
    })
    main.appendChild(footer)

    tocState.items = items
    tocState.sidebar = toc
    tocState.list = list
    updateActive()
  }

  function setActive(id) {
    tocState.items.forEach(function (entry) {
      entry.item.classList.toggle('active', entry.id === id)
    })

    var active = tocState.list && tocState.list.querySelector('.toc-item.active')
    if (!active || !tocState.sidebar) return

    if (isMobileToc()) {
      active.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
      return
    }

    var sidebar = tocState.sidebar
    var sidebarRect = sidebar.getBoundingClientRect()
    var itemRect = active.getBoundingClientRect()
    if (itemRect.top < sidebarRect.top + 8 || itemRect.bottom > sidebarRect.bottom - 8) {
      active.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
  }

  function updateActive() {
    if (!tocState.items.length) return

    var offset = 88
    var current = tocState.items[0]
    var docHeight = document.documentElement.scrollHeight
    var scrollBottom = window.scrollY + window.innerHeight

    // 滚到页面底部时，强制高亮最后一节
    if (scrollBottom >= docHeight - 48) {
      current = tocState.items[tocState.items.length - 1]
      setActive(current.id)
      return
    }

    for (var i = 0; i < tocState.items.length; i++) {
      var headTop = tocState.items[i].head.getBoundingClientRect().top
      if (headTop <= offset) current = tocState.items[i]
    }

    setActive(current.id)
  }

  function setupScrollSpy() {
    window.addEventListener('scroll', updateActive, { passive: true })
    window.addEventListener('resize', updateActive, { passive: true })
  }

  function showError(message) {
    var layout = document.getElementById('doc-layout')
    if (layout) {
      layout.innerHTML = '<div class="doc-error">' + message + '</div>'
    }
  }

  function boot() {
    setupScrollSpy()

    var doc = getDocKind()
    var lang = getLang()
    renderLangSwitch(lang)

    var main = document.getElementById('doc-main')
    if (main) {
      main.innerHTML =
        '<div class="doc-loading">' + (lang === 'en-US' ? 'Loading…' : '加载中…') + '</div>'
    }

    loadDocData(doc, lang)
      .catch(function () {
        if (lang !== 'zh-CN') return loadDocData(doc, 'zh-CN')
        throw new Error('load failed')
      })
      .then(function (data) {
        if (!data) throw new Error('empty doc')
        renderDoc(data, lang)
      })
      .catch(function () {
        showError(lang === 'en-US' ? 'Failed to load document.' : '文档加载失败。')
      })
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot)
  } else {
    boot()
  }
})()
