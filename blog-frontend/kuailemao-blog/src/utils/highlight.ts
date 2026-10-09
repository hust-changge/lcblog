// 轻量代码高亮：单次扫描源码文本，先转义再包裹 span，不引入第三方库，也不从 CDN 加载脚本。
const WORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double else enum ' +
    'extends final finally float for if implements import instanceof int interface long native new package private ' +
    'protected public return short static super switch synchronized this throw throws transient try void volatile ' +
    'while var record sealed permits yield null true false function let async await export def lambda pass elif ' +
    'with as in is not and or None True False fi then done esac echo').split(' '))
const SQL = new Set(('select from where insert into update delete join left right inner outer on group by order having limit ' +
    'offset values set create table alter drop index primary key foreign references unique not null default and or ' +
    'as distinct count case when then else end in is like between union all exists begin commit rollback').split(' '))

function esc(s: string): string {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function highlight(code: HTMLElement) {
    if (code.dataset.highlighted) return
    const lang = (code.className.match(/language-([\w+-]+)/) || [])[1] || ''
    const sql = lang === 'sql' || lang === 'mysql'
    const hash = /^(python|py|bash|sh|shell|zsh|yaml|yml|properties|dockerfile|toml|conf|nginx)$/.test(lang)
    const comments = ['\\/\\*[\\s\\S]*?\\*\\/', hash ? '#[^\\n]*' : '\\/\\/[^\\n]*']
    if (sql) comments.push('--[^\\n]*')
    const re = new RegExp('(' + comments.join('|') + ')' +
        '|("(?:\\\\.|[^"\\\\\\n])*"|\'(?:\\\\.|[^\'\\\\\\n])*\')' +
        '|(\\b\\d+(?:\\.\\d+)?[lLfFdD]?\\b)' +
        '|(@?[A-Za-z_]\\w*)', 'g')
    const src = code.textContent ?? ''
    if (src.length > 200000) return
    let out = ''
    let last = 0
    let m: RegExpExecArray | null
    while ((m = re.exec(src)) !== null) {
        out += esc(src.slice(last, m.index))
        last = re.lastIndex
        let cls = ''
        if (m[1]) cls = 'tok-comment'
        else if (m[2]) cls = 'tok-string'
        else if (m[3]) cls = 'tok-number'
        else if (m[4].startsWith('@')) cls = 'tok-annotation'
        else if (sql ? SQL.has(m[4].toLowerCase()) : WORDS.has(m[4])) cls = 'tok-keyword'
        out += cls ? `<span class="${cls}">${esc(m[0])}</span>` : esc(m[0])
    }
    code.innerHTML = out + esc(src.slice(last))
    code.dataset.highlighted = '1'
}

export function highlightAll(root: ParentNode) {
    root.querySelectorAll<HTMLElement>('pre > code').forEach(highlight)
}
