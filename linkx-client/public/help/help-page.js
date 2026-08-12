/**
 * 作者：yangleduo
 */
/* global window, document */
(function () {
  'use strict'

  var state = {
    lang: 'zh-CN',
    articleId: '',
    data: null,
    tocItems: []
  }

  function getLang() {
    var params = new URLSearchParams(window.location.search)
    return params.get('lang') === 'en-US' ? 'en-US' : 'zh-CN'
  }

  function getArticleId() {
    var params = new URLSearchParams(window.location.search)
    var id = (params.get('article') || '').trim()
    return id === 'home' ? '' : id
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

  function loadHelpData(lang) {
    var store = window.__HELP_DATA__
    if (store && store[lang]) return Promise.resolve(store[lang])
    return loadScript('./data/catalog.' + lang + '.js').then(function () {
      return window.__HELP_DATA__[lang]
    })
  }

  function findCategoryTitle(data, articleId) {
    for (var i = 0; i < data.categories.length; i++) {
      var cat = data.categories[i]
      for (var j = 0; j < cat.articles.length; j++) {
        if (cat.articles[j].id === articleId) return cat.title
      }
    }
    return ''
  }

  function renderBlock(block, labels) {
    if (!block || !block.type) return null

    if (block.type === 'p') {
      var p = document.createElement('p')
      p.className = 'doc-p'
      if (block.html) p.innerHTML = block.html
      else p.textContent = block.text || ''
      return p
    }

    if (block.type === 'ul' || block.type === 'ol') {
      var list = document.createElement(block.type)
      list.className = 'doc-list'
      ;(block.items || []).forEach(function (item) {
        var li = document.createElement('li')
        if (typeof item === 'string' && item.indexOf('<') >= 0) li.innerHTML = item
        else li.textContent = item
        list.appendChild(li)
      })
      return list
    }

    if (block.type === 'steps') {
      var steps = document.createElement('ol')
      steps.className = 'step-list'
      ;(block.items || []).forEach(function (item) {
        var li = document.createElement('li')
        if (typeof item === 'string' && item.indexOf('<') >= 0) li.innerHTML = item
        else li.textContent = item
        steps.appendChild(li)
      })
      return steps
    }

    if (block.type === 'tip' || block.type === 'note' || block.type === 'warn') {
      var callout = document.createElement('div')
      callout.className = 'callout callout-' + (block.type === 'warn' ? 'warn' : block.type === 'note' ? 'note' : 'tip')
      var label = document.createElement('span')
      label.className = 'callout-label'
      label.textContent =
        block.type === 'warn'
          ? labels.warn || '注意'
          : block.type === 'note'
            ? labels.note || '说明'
            : labels.tip || '提示'
      callout.appendChild(label)
      var body = document.createElement('span')
      if (block.html) body.innerHTML = block.html
      else body.textContent = block.text || ''
      callout.appendChild(body)
      return callout
    }

    if (block.type === 'faq') {
      var faq = document.createElement('ul')
      faq.className = 'faq-list'
      ;(block.items || []).forEach(function (item) {
        var li = document.createElement('li')
        li.className = 'faq-item'
        var q = document.createElement('p')
        q.className = 'faq-q'
        q.textContent = item.q || ''
        var a = document.createElement('p')
        a.className = 'faq-a'
        a.textContent = item.a || ''
        li.appendChild(q)
        li.appendChild(a)
        faq.appendChild(li)
      })
      return faq
    }

    return null
  }

  function renderTopbar(lang, data) {
    var bar = document.getElementById('topbar')
    if (!bar) return
    bar.innerHTML =
      '<div class="topbar-inner">' +
      '<a class="brand" href="./index.html?lang=' + lang + '">' +
      '<span class="brand-mark">L</span><span>' + (data.brandTitle || 'LinkX') + '</span></a>' +
      '<div class="lang-switch">' +
      '<button type="button" data-lang="zh-CN"' + (lang === 'zh-CN' ? ' class="active"' : '') + '>中文</button>' +
      '<span class="lang-sep">|</span>' +
      '<button type="button" data-lang="en-US"' + (lang === 'en-US' ? ' class="active"' : '') + '>English</button>' +
      '</div></div>'
    bar.querySelectorAll('[data-lang]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var next = btn.getAttribute('data-lang')
        if (next === lang) return
        var url = new URL(window.location.href)
        url.searchParams.set('lang', next)
        window.location.href = url.href
      })
    })
    document.title = data.siteTitle || 'LinkX Help'
  }

  function navigateToArticle(id) {
    var url = new URL(window.location.href)
    if (id) url.searchParams.set('article', id)
    else url.searchParams.delete('article')
    window.history.replaceState({}, '', url.href)
    state.articleId = id
    renderAll()
  }

  function getFilteredCategories(data, query) {
    var q = (query || '').trim().toLowerCase()
    if (!q) return data.categories
    return data.categories
      .map(function (cat) {
        return {
          id: cat.id,
          title: cat.title,
          articles: cat.articles.filter(function (article) {
            return (
              article.title.toLowerCase().indexOf(q) >= 0 ||
              (article.description || '').toLowerCase().indexOf(q) >= 0
            )
          })
        }
      })
      .filter(function (cat) { return cat.articles.length > 0 })
  }

  function renderNav() {
    var nav = document.getElementById('help-nav')
    var data = state.data
    if (!nav || !data) return

    var searchInput = nav.querySelector('.nav-search-input')
    var query = searchInput ? searchInput.value : ''
    var categories = getFilteredCategories(data, query)

    if (!nav.querySelector('.nav-tree')) {
      nav.innerHTML =
        '<div class="nav-search"><input class="nav-search-input" type="search" placeholder="' +
        (data.searchPlaceholder || '') +
        '" /></div><div class="nav-tree"></div>'
      nav.querySelector('.nav-search-input').addEventListener('input', renderNav)
    }

    var tree = nav.querySelector('.nav-tree')
    tree.innerHTML = ''

    var homeBtn = document.createElement('button')
    homeBtn.type = 'button'
    homeBtn.className = 'nav-article' + (!state.articleId ? ' active' : '')
    homeBtn.textContent = data.homeNavLabel || '首页'
    homeBtn.addEventListener('click', function () { navigateToArticle('') })
    tree.appendChild(homeBtn)

    if (!categories.length) {
      var empty = document.createElement('p')
      empty.className = 'nav-empty'
      empty.textContent = data.noResults || ''
      tree.appendChild(empty)
      return
    }

    categories.forEach(function (category) {
      var block = document.createElement('div')
      block.className = 'nav-category'
      var title = document.createElement('h3')
      title.className = 'nav-category-title'
      title.textContent = category.title
      block.appendChild(title)

      category.articles.forEach(function (article) {
        var btn = document.createElement('button')
        btn.type = 'button'
        btn.className = 'nav-article' + (article.id === state.articleId ? ' active' : '')
        btn.textContent = article.title
        btn.addEventListener('click', function () { navigateToArticle(article.id) })
        block.appendChild(btn)
      })

      tree.appendChild(block)
    })
  }

  function renderHome() {
    var home = document.getElementById('view-home')
    var data = state.data
    if (!home || !data) return

    home.hidden = false
    home.innerHTML =
      '<div class="home-hero">' +
      '<h1>' + (data.homeTitle || '') + '</h1>' +
      '<p>' + (data.homeSubtitle || '') + '</p>' +
      '<div class="home-search"><input type="search" id="home-search" placeholder="' +
      (data.searchPlaceholder || '') + '" /></div></div>' +
      '<div class="home-grid" id="home-grid"></div>' +
      '<div class="home-quick"><h2>' + (data.quickTitle || '') + '</h2><div class="home-quick-list" id="home-quick"></div></div>'

    var grid = home.querySelector('#home-grid')
    ;(data.categoryCards || []).forEach(function (card) {
      var el = document.createElement('button')
      el.type = 'button'
      el.className = 'home-card'
      el.innerHTML =
        '<div class="home-card-icon">' + (card.icon || '📘') + '</div>' +
        '<h3 class="home-card-title">' + card.title + '</h3>' +
        '<p class="home-card-desc">' + (card.description || '') + '</p>'
      el.addEventListener('click', function () {
        var first = findFirstArticleInCategory(data, card.id)
        if (first) navigateToArticle(first)
      })
      grid.appendChild(el)
    })

    var quick = home.querySelector('#home-quick')
    ;(data.quickLinks || []).forEach(function (link) {
      var a = document.createElement('a')
      a.href = '#'
      a.textContent = link.title
      a.addEventListener('click', function (e) {
        e.preventDefault()
        navigateToArticle(link.articleId)
      })
      quick.appendChild(a)
    })

    var homeSearch = home.querySelector('#home-search')
    homeSearch.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter') return
      var q = homeSearch.value.trim().toLowerCase()
      if (!q) return
      for (var i = 0; i < data.categories.length; i++) {
        for (var j = 0; j < data.categories[i].articles.length; j++) {
          var art = data.categories[i].articles[j]
          if (
            art.title.toLowerCase().indexOf(q) >= 0 ||
            (art.description || '').toLowerCase().indexOf(q) >= 0
          ) {
            navigateToArticle(art.id)
            return
          }
        }
      }
    })
  }

  function findFirstArticleInCategory(data, categoryId) {
    for (var i = 0; i < data.categories.length; i++) {
      if (data.categories[i].id === categoryId && data.categories[i].articles.length) {
        return data.categories[i].articles[0].id
      }
    }
    return ''
  }

  function renderBreadcrumb(article) {
    var crumb = document.getElementById('breadcrumb')
    if (!crumb || !state.data) return
    if (!article) {
      crumb.hidden = true
      return
    }
    crumb.hidden = false
    var catTitle = findCategoryTitle(state.data, state.articleId)
    crumb.innerHTML =
      '<a href="./index.html?lang=' + state.lang + '">' + (state.data.breadcrumbHome || '帮助中心') + '</a>' +
      '<span class="breadcrumb-sep">›</span><span>' + catTitle + '</span>' +
      '<span class="breadcrumb-sep">›</span><span>' + article.title + '</span>'
  }

  function scrollToSection(id) {
    var head = document.getElementById(id)
    if (head) head.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function renderToc(sections) {
    var toc = document.getElementById('doc-toc')
    if (!toc) return
    toc.innerHTML = ''
    if (!sections.length) {
      toc.hidden = true
      state.tocItems = []
      return
    }
    toc.hidden = false

    var title = document.createElement('h3')
    title.className = 'toc-title'
    title.textContent = state.data.tocTitle || '本页目录'
    toc.appendChild(title)

    var list = document.createElement('ul')
    list.className = 'toc-list'
    toc.appendChild(list)

    state.tocItems = []
    sections.forEach(function (section) {
      var li = document.createElement('li')
      li.className = 'toc-item level-' + (section.level || 2)
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
      state.tocItems.push({ id: section.id, head: null, item: li })
    })
  }

  function setActiveToc(id) {
    state.tocItems.forEach(function (entry) {
      entry.item.classList.toggle('active', entry.id === id)
    })
  }

  function bindScrollSpy() {
    function onScroll() {
      if (!state.tocItems.length) return
      var offset = 80
      var current = state.tocItems[0]
      var scrollBottom = window.scrollY + window.innerHeight
      if (scrollBottom >= document.documentElement.scrollHeight - 48) {
        setActiveToc(state.tocItems[state.tocItems.length - 1].id)
        return
      }
      for (var i = 0; i < state.tocItems.length; i++) {
        var head = document.getElementById(state.tocItems[i].id)
        state.tocItems[i].head = head
        if (head && head.getBoundingClientRect().top <= offset) current = state.tocItems[i]
      }
      setActiveToc(current.id)
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll, { passive: true })
    onScroll()
  }

  function renderArticle() {
    var main = document.getElementById('doc-main')
    var data = state.data
    if (!main || !data) return

    var article = data.articles[state.articleId]
    if (!article) {
      main.hidden = false
      main.innerHTML = '<div class="doc-error">' + (data.articleNotFound || '') + '</div>'
      renderToc([])
      renderBreadcrumb(null)
      return
    }

    main.hidden = false
    main.innerHTML = ''

    var h1 = document.createElement('h1')
    h1.className = 'article-title'
    h1.textContent = article.title
    main.appendChild(h1)

    if (article.goal) {
      var goal = document.createElement('p')
      goal.className = 'article-goal'
      goal.textContent = article.goal
      main.appendChild(goal)
    }

    var tocSections = []
    var labels = data.labels || {}

    ;(article.sections || []).forEach(function (section) {
      var sec = document.createElement('section')
      sec.className = 'doc-section'

      var level = section.level === 3 ? 3 : 2
      var headTag = level === 3 ? 'h3' : 'h2'
      var head = document.createElement(headTag)
      head.className = 'section-title level-' + level
      head.id = section.id
      head.textContent = section.title
      sec.appendChild(head)

      ;(section.content || []).forEach(function (block) {
        var el = renderBlock(block, labels)
        if (el) sec.appendChild(el)
      })

      main.appendChild(sec)
      tocSections.push({ id: section.id, title: section.title, level: level })
    })

    if (article.related && article.related.length) {
      var related = document.createElement('div')
      related.className = 'related-links'
      var rh = document.createElement('h3')
      rh.textContent = data.relatedTitle || '相关帮助'
      related.appendChild(rh)
      article.related.forEach(function (id) {
        var ref = data.articles[id]
        if (!ref) return
        var a = document.createElement('a')
        a.href = '#'
        a.textContent = ref.title
        a.addEventListener('click', function (e) {
          e.preventDefault()
          navigateToArticle(id)
        })
        related.appendChild(a)
      })
      main.appendChild(related)
    }

    renderToc(tocSections)
    renderBreadcrumb(article)
    window.scrollTo(0, 0)
  }

  function setLayoutMode() {
    var shell = document.getElementById('shell')
    var nav = document.getElementById('help-nav')
    var toc = document.getElementById('doc-toc')
    var home = document.getElementById('view-home')
    var main = document.getElementById('doc-main')
    var crumb = document.getElementById('breadcrumb')

    if (!state.articleId) {
      shell.classList.add('is-home')
      if (nav) nav.hidden = true
      if (toc) toc.hidden = true
      if (main) main.hidden = true
      if (crumb) crumb.hidden = true
      renderHome()
      return
    }

    shell.classList.remove('is-home')
    if (nav) nav.hidden = false
    if (home) home.hidden = true
    renderArticle()
  }

  function renderAll() {
    renderNav()
    setLayoutMode()
  }

  function showError(message) {
    var shell = document.getElementById('shell')
    if (shell) shell.innerHTML = '<div class="doc-error">' + message + '</div>'
  }

  function boot() {
    state.lang = getLang()
    state.articleId = getArticleId()

    loadHelpData(state.lang)
      .catch(function () {
        if (state.lang !== 'zh-CN') return loadHelpData('zh-CN')
        throw new Error('load failed')
      })
      .then(function (data) {
        if (!data) throw new Error('empty')
        state.data = data
        if (state.articleId && !data.articles[state.articleId]) {
          state.articleId = ''
        }
        renderTopbar(state.lang, data)
        renderAll()
        bindScrollSpy()
      })
      .catch(function () {
        showError(state.lang === 'en-US' ? 'Failed to load help.' : '帮助文档加载失败。')
      })
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot)
  } else {
    boot()
  }
})()
