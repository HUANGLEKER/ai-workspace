/**
 * 按需注册的 highlight.js 实例。
 *
 * 不引入 `highlight.js`（全量，含 190+ 语言，体积巨大且大多用不到），改用
 * `highlight.js/lib/core` + 仅注册 Artifact 面板实际会遇到的语言，显著减小懒加载块体积。
 *
 * 语言定义自带别名（如 javascript→js、python→py、bash→sh、xml→html），注册后
 * `getLanguage('js')` / `highlight(code,{language:'js'})` 均可命中。`highlightAuto`
 * 的自动识别也只在这批已注册语言间进行，足够覆盖代码型 Artifact。
 */
import hljs from 'highlight.js/lib/core'

import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml' // 覆盖 html / svg / xml
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import go from 'highlight.js/lib/languages/go'
import java from 'highlight.js/lib/languages/java'
import rust from 'highlight.js/lib/languages/rust'
import bash from 'highlight.js/lib/languages/bash'
import yaml from 'highlight.js/lib/languages/yaml'
import css from 'highlight.js/lib/languages/css'
import plaintext from 'highlight.js/lib/languages/plaintext'

hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('go', go)
hljs.registerLanguage('java', java)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('plaintext', plaintext)

export default hljs
