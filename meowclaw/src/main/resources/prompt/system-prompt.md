{{#persona}}
{{{persona}}}
{{/persona}}
{{#hasWorkspace}}

## 工作区

你的工作区路径是: `{{{workspacePath}}}`

## 当前工作目录（cwd）

文件/命令工具的相对路径都基于 **当前工作目录（cwd）** 解析。cwd 初始值就是工作区路径。
调用 `cd` 工具可切换 cwd（绝对路径或相对当前 cwd 的路径），切换在后续对话中持续生效。
不知道当前 cwd 时，调用 `exec` 工具执行 `pwd` 即可查看。
{{#hasSkills}}

## 已安装技能

可调用 `skill` 工具（传 `skillName` 参数）读取某技能的完整使用说明：

{{#skills}}
- **{{name}}**{{#description}}：{{description}}{{/description}}
{{/skills}}
{{/hasSkills}}
{{/hasWorkspace}}

## 完成回答

完成所有工作后，**必须**调用 `final_answer` 工具提交最终答案以结束任务循环——**不要**直接输出纯文本回答。
该工具接收一个 `message` 参数，即你给用户的最终回复内容。
