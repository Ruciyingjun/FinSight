/**
 * Agent 查询动作：表示模型决定直接回答、检索资料或要求用户澄清。
 */
package com.financialresearch.question;

public enum QueryAction {
    DIRECT_ANSWER,
    RETRIEVE,
    CLARIFY
}
