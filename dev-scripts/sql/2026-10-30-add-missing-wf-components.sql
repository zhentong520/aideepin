INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable, is_deleted) VALUES
('6762025efc0b4cea893d2f55e48e164b','FaqExtractor','常见问题提取','从输入文本中提取常见问答对（FAQ）。',25,true,false),
('0d0dd7203cb54a2bbd3c4b92d48f53a6','OpenAiImage','OpenAI 画图','调用 OpenAI DALL·E 按描述生成图片。',26,true,false),
('519d16caa75c4a2c86ecdda5ec6b90d8','Tongyiwanx','通义万相-画图','调用通义万相（dashscope）按描述生成图片。',27,true,false),
('497dd89af35644fda6910377aa18f394','HumanFeedback','人机交互','暂停流程，等待人工输入后继续执行。',28,true,false),
('a028ff6386c943a09f11cf41456f946e','MailSend','邮件发送','把内容以邮件形式发送给指定收件人。',29,true,false),
('7d54d559a6a04d7f854f8b84c0f4d2d2','HttpRequest','HTTP 请求','调用外部 HTTP 接口并取回响应。',30,true,false),
('c56c7dcc890b4f0f952cbf680c15c796','Agent','Agent 节点','让已配置的 Agent（自带知识库/MCP/联网能力）处理输入。',31,true,false),
('16fd3476be404d599b5d73fbede4cb5e','Database','数据库查询','在外部数据源上执行只读 SQL 并返回结果。',32,true,false),
('078eb84ff99b4d8aa93dd1e69776fdef','Mcp','MCP 工具','调用 MCP 服务提供的工具并返回执行结果。',33,true,false),
('ab01e5ace8df481696c4dc90e98b4bde','Skill','技能','执行技能：读取技能指令正文或运行技能脚本。',34,true,false)
ON CONFLICT (name, is_deleted) DO NOTHING;
