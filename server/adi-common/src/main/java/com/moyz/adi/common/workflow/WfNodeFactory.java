package com.moyz.adi.common.workflow;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import com.moyz.adi.common.workflow.node.EndNode;
import com.moyz.adi.common.workflow.node.agent.AgentNode;
import com.moyz.adi.common.workflow.node.answer.LLMAnswerNode;
import com.moyz.adi.common.workflow.node.classifier.ClassifierNode;
import com.moyz.adi.common.workflow.node.database.DatabaseNode;
import com.moyz.adi.common.workflow.node.mcp.McpNode;
import com.moyz.adi.common.workflow.node.openaiimage.OpenAiImageNode;
import com.moyz.adi.common.workflow.node.documentextractor.DocumentExtractorNode;
import com.moyz.adi.common.workflow.node.faqextractor.FaqExtractorNode;
import com.moyz.adi.common.workflow.node.google.GoogleNode;
import com.moyz.adi.common.workflow.node.httprequest.HttpRequestNode;
import com.moyz.adi.common.workflow.node.humanfeedback.HumanFeedbackNode;
import com.moyz.adi.common.workflow.node.keywordextractor.KeywordExtractorNode;
import com.moyz.adi.common.workflow.node.knowledgeretrieval.KnowledgeRetrievalNode;
import com.moyz.adi.common.workflow.node.mailsender.MailSendNode;
import com.moyz.adi.common.workflow.node.orchestrator.OrchestratorNode;
import com.moyz.adi.common.workflow.node.skill.SkillNode;
import com.moyz.adi.common.workflow.node.start.StartNode;
import com.moyz.adi.common.workflow.node.switcher.SwitcherNode;
import com.moyz.adi.common.workflow.node.template.TemplateNode;
import com.moyz.adi.common.workflow.node.tongyiwanx.TongyiwanxNode;

public class WfNodeFactory {
    public static AbstractWfNode create(WorkflowComponent wfComponent, WorkflowNode nodeDefinition, WfState wfState, WfNodeState nodeState) {
        //注意：必须先判空再进 switch。getByName 返回 null 时，switch 会在到达 default 之前
        //就抛 NPE（NPE: Cannot invoke "...ordinal()" because ... is null），
        //调用方只能看到一个与本问题无关的空指针堆栈。
        //Resolve first: switching on a null enum throws NPE before the default branch is reached.
        WfComponentNameEnum componentName = WfComponentNameEnum.getByName(wfComponent.getName());
        if (null == componentName) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                    "unsupported workflow component: " + wfComponent.getName());
        }
        AbstractWfNode wfNode;
        switch (componentName) {
            case START:
                wfNode = new StartNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case LLM_ANSWER:
                wfNode = new LLMAnswerNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case CLASSIFIER:
                wfNode = new ClassifierNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case SWITCHER:
                wfNode = new SwitcherNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case TEMPLATE:
                wfNode = new TemplateNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case KEYWORD_EXTRACTOR:
                wfNode = new KeywordExtractorNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case DOCUMENT_EXTRACTOR:
                wfNode = new DocumentExtractorNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case FAQ_EXTRACTOR:
                wfNode = new FaqExtractorNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case KNOWLEDGE_RETRIEVER:
                wfNode = new KnowledgeRetrievalNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case GOOGLE_SEARCH:
                wfNode = new GoogleNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case OPENAI_IMAGE:
                wfNode = new OpenAiImageNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case TONGYI_WANX:
                wfNode = new TongyiwanxNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case HUMAN_FEEDBACK:
                wfNode = new HumanFeedbackNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case MAIL_SEND:
                wfNode = new MailSendNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case HTTP_REQUEST:
                wfNode = new HttpRequestNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case AGENT:
                wfNode = new AgentNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case DATABASE:
                wfNode = new DatabaseNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case MCP:
                wfNode = new McpNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case SKILL:
                wfNode = new SkillNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case ORCHESTRATOR:
                wfNode = new OrchestratorNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            case END:
                wfNode = new EndNode(wfComponent, nodeDefinition, wfState, nodeState);
                break;
            default:
                //显式报错：以前这里静默返回 null，调用方会以 NPE 形式炸在很远的地方
                //Fail loudly instead of returning null, which surfaced as an unrelated NPE downstream
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                        "unsupported workflow component: " + wfComponent.getName());
        }
        return wfNode;
    }
}
