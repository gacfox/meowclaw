<runtime_context>
  <current_date>{{currentDate}}</current_date>
  <operating_system>{{osName}}</operating_system>
{{#hasWorkspace}}
  <workspace_root>{{{workspacePath}}}</workspace_root>
  <current_working_directory>{{{cwd}}}</current_working_directory>
{{/hasWorkspace}}
  <installed_skills>
{{#hasSkills}}
{{#skills}}
    <skill name="{{name}}">{{#description}}{{description}}{{/description}}</skill>
{{/skills}}
{{/hasSkills}}
{{^hasSkills}}
    （暂无技能）
{{/hasSkills}}
  </installed_skills>
</runtime_context>
{{#hasRecap}}

<recap_update mode="{{recapMode}}">
{{recap}}
</recap_update>
{{/hasRecap}}
{{#hasMemories}}

<retrieved_memories>
{{memories}}
</retrieved_memories>
{{/hasMemories}}