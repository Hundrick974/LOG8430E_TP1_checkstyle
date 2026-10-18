///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.utils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

/**
 * Determines whether statement subtrees terminate execution.
 */
final class TerminationUtil {

    /** Prevents instances. */
    private TerminationUtil() {
    }

    /**
     * Checks if a given subtree is terminated by return, throw, break, continue, or yield.
     *
     * @param ast root of given subtree
     * @return true if the subtree is terminated.
     */
    static boolean isTerminated(final DetailAST ast) {
        return isTerminated(ast, true, true, new HashSet<>());
    }

    /**
     * Checks if a given subtree terminated by return, throw, yield or,
     * if allowed break, continue.
     * When analyzing fall-through cases in switch statements, a Set of String labels
     * is used to keep track of the labels encountered in the enclosing switch statements.
     *
     * @param ast root of given subtree
     * @param useBreak should we consider break as terminator
     * @param useContinue should we consider continue as terminator
     * @param labelsForCurrentSwitchScope the Set labels for the current scope of the switch
     * @return true if the subtree is terminated.
     */
    private static boolean isTerminated(final DetailAST ast, boolean useBreak, boolean useContinue,
                                        Set<String> labelsForCurrentSwitchScope) {

        return switch (ast.getType()) {
            case TokenTypes.LITERAL_RETURN, TokenTypes.LITERAL_YIELD,
                    TokenTypes.LITERAL_THROW -> true;
            case TokenTypes.LITERAL_BREAK -> useBreak
                    || hasLabel(ast, labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_CONTINUE -> useContinue
                    || hasLabel(ast, labelsForCurrentSwitchScope);
            case TokenTypes.SLIST -> checkSlist(ast, useBreak, useContinue,
                    labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_IF -> checkIf(ast, useBreak, useContinue,
                    labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_FOR, TokenTypes.LITERAL_WHILE, TokenTypes.LITERAL_DO ->
                checkLoop(ast, labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_TRY -> checkTry(ast, useBreak, useContinue,
                    labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_SWITCH -> checkSwitch(ast, useContinue,
                    labelsForCurrentSwitchScope);
            case TokenTypes.LITERAL_SYNCHRONIZED ->
                checkSynchronized(ast, useBreak, useContinue,
                    labelsForCurrentSwitchScope);
            case TokenTypes.LABELED_STAT -> {
                labelsForCurrentSwitchScope.add(ast.getFirstChild().getText());
                yield isTerminated(ast.getLastChild(), useBreak, useContinue,
                        labelsForCurrentSwitchScope);
            }
            default -> false;
        };
    }

    /**
     * Checks if given break or continue ast has outer label.
     *
     * @param statement break or continue node
     * @param labelsForCurrentSwitchScope the Set labels for the current scope of the switch
     * @return true if local label used
     */
    private static boolean hasLabel(DetailAST statement, Set<String> labelsForCurrentSwitchScope) {
        return Optional.ofNullable(statement)
                .map(DetailAST::getFirstChild)
                .filter(child -> child.getType() == TokenTypes.IDENT)
                .map(DetailAST::getText)
                .filter(label -> !labelsForCurrentSwitchScope.contains(label))
                .isPresent();
    }

    /**
     * Checks if a given SLIST terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param slistAst SLIST to check
     * @param useBreak should we consider break as terminator
     * @param useContinue should we consider continue as terminator
     * @param labels label names
     * @return true if SLIST is terminated.
     */
    private static boolean checkSlist(final DetailAST slistAst, boolean useBreak,
                                      boolean useContinue, Set<String> labels) {
        DetailAST lastStmt = slistAst.getLastChild();

        if (lastStmt.getType() == TokenTypes.RCURLY) {
            lastStmt = lastStmt.getPreviousSibling();
        }

        while (TokenUtil.isOfType(lastStmt, TokenTypes.SINGLE_LINE_COMMENT,
                TokenTypes.BLOCK_COMMENT_BEGIN)) {
            lastStmt = lastStmt.getPreviousSibling();
        }

        return lastStmt != null
            && isTerminated(lastStmt, useBreak, useContinue, labels);
    }

    /**
     * Checks if a given IF terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param ast IF to check
     * @param useBreak should we consider break as terminator
     * @param useContinue should we consider continue as terminator
     * @param labels label names
     * @return true if IF is terminated.
     */
    private static boolean checkIf(final DetailAST ast, boolean useBreak,
                                   boolean useContinue, Set<String> labels) {
        final DetailAST thenStmt =
                TokenUtil.getNextNonCommentAst(ast.findFirstToken(TokenTypes.RPAREN));

        final DetailAST elseStmt = ast.findFirstToken(TokenTypes.LITERAL_ELSE);

        final boolean isTerminated;

        if (checkInfiniteLoop(ast.getParent().getParent())) {
            isTerminated = isTerminated(thenStmt, useBreak, useContinue, labels)
                    || elseStmt != null
                    && isTerminated(elseStmt.getLastChild(), useBreak, useContinue, labels);
        }
        else {
            isTerminated = elseStmt != null
                    && isTerminated(thenStmt, useBreak, useContinue, labels)
                    && isTerminated(elseStmt.getLastChild(), useBreak, useContinue, labels);
        }

        return isTerminated;
    }

    /**
     * Checks if a given loop terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param ast loop to check
     * @param labels label names
     * @return true if loop is terminated.
     */
    private static boolean checkLoop(final DetailAST ast, Set<String> labels) {
        final DetailAST loopBody;
        if (ast.getType() == TokenTypes.LITERAL_DO) {
            final DetailAST lparen = ast.findFirstToken(TokenTypes.DO_WHILE);
            loopBody = lparen.getPreviousSibling();
        }
        else {
            final DetailAST rparen = ast.findFirstToken(TokenTypes.RPAREN);
            loopBody = rparen.getNextSibling();
        }

        return checkInfiniteLoop(ast) && !isTerminated(loopBody, true, false, labels)
                || isTerminated(loopBody, false, false, labels);
    }

    /**
     * Checks if a given try/catch/finally block terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param ast loop to check
     * @param useBreak should we consider break as terminator
     * @param useContinue should we consider continue as terminator
     * @param labels label names
     * @return true if try/catch/finally block is terminated
     */
    private static boolean checkTry(final DetailAST ast, boolean useBreak,
                                    boolean useContinue, Set<String> labels) {
        final DetailAST finalStmt = ast.getLastChild();
        boolean isTerminated = finalStmt.getType() == TokenTypes.LITERAL_FINALLY
                && isTerminated(finalStmt.findFirstToken(TokenTypes.SLIST),
                useBreak, useContinue, labels);

        if (!isTerminated) {
            DetailAST firstChild = ast.getFirstChild();

            if (firstChild.getType() == TokenTypes.RESOURCE_SPECIFICATION) {
                firstChild = firstChild.getNextSibling();
            }

            isTerminated = isTerminated(firstChild,
                    useBreak, useContinue, labels);

            DetailAST catchStmt = ast.findFirstToken(TokenTypes.LITERAL_CATCH);
            while (catchStmt != null
                    && isTerminated
                    && catchStmt.getType() == TokenTypes.LITERAL_CATCH) {
                final DetailAST catchBody =
                        catchStmt.findFirstToken(TokenTypes.SLIST);
                isTerminated = isTerminated(catchBody, useBreak, useContinue, labels);
                catchStmt = catchStmt.getNextSibling();
            }
        }
        return isTerminated;
    }

    /**
     * Checks if a given switch terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param literalSwitchAst loop to check
     * @param useContinue should we consider continue as terminator
     * @param labels label names
     * @return true if switch is terminated
     */
    private static boolean checkSwitch(DetailAST literalSwitchAst,
                                       boolean useContinue, Set<String> labels) {
        DetailAST caseGroup = literalSwitchAst.findFirstToken(TokenTypes.CASE_GROUP);
        boolean isTerminated = caseGroup != null;
        while (isTerminated && caseGroup.getType() != TokenTypes.RCURLY) {
            final DetailAST caseBody =
                caseGroup.findFirstToken(TokenTypes.SLIST);
            isTerminated = caseBody != null
                    && isTerminated(caseBody, false, useContinue, labels);
            caseGroup = caseGroup.getNextSibling();
        }
        return isTerminated;
    }

    /**
     * Checks if a given synchronized block terminated by return, throw or,
     * if allowed break, continue.
     *
     * @param synchronizedAst synchronized block to check.
     * @param useBreak should we consider break as terminator
     * @param useContinue should we consider continue as terminator
     * @param labels label names
     * @return true if synchronized block is terminated
     */
    private static boolean checkSynchronized(final DetailAST synchronizedAst, boolean useBreak,
                                      boolean useContinue, Set<String> labels) {
        return isTerminated(
            synchronizedAst.findFirstToken(TokenTypes.SLIST), useBreak, useContinue, labels);
    }

    /**
     * Checks whether the given ast is an infinite loop.
     *
     * @param ast the token to check
     * @return true if the token is an infinite, false otherwise
     */
    private static boolean checkInfiniteLoop(DetailAST ast) {
        boolean isInfiniteLoop = false;
        if (ast.getType() == TokenTypes.LITERAL_FOR) {
            isInfiniteLoop = checkForLoop(ast);
        }
        else if (ast.getType() == TokenTypes.LITERAL_WHILE
                || ast.getType() == TokenTypes.LITERAL_DO) {
            final DetailAST lparen = NullUtil.notNull(ast.findFirstToken(TokenTypes.LPAREN));
            final DetailAST expression = NullUtil.notNull(lparen.getNextSibling());
            final DetailAST firstChild = NullUtil.notNull(expression.getFirstChild());
            isInfiniteLoop = firstChild.getType() == TokenTypes.LITERAL_TRUE;
        }
        return isInfiniteLoop;
    }

    /**
     * Checks if a for loop is infinite (empty or {@code true} condition).
     *
     * @param forLoop the for loop to check
     * @return true if the for loop is infinite
     */
    private static boolean checkForLoop(DetailAST forLoop) {
        boolean isInfiniteLoop = false;
        if (forLoop.findFirstToken(TokenTypes.FOR_EACH_CLAUSE) == null) {
            final DetailAST condition =
                    NullUtil.notNull(forLoop.findFirstToken(TokenTypes.FOR_CONDITION));
            DetailAST child = condition.getFirstChild();
            if (child != null) {
                // EXPR node always has child
                child = NullUtil.notNull(child.getFirstChild());
            }
            isInfiniteLoop = child == null || child.getType() == TokenTypes.LITERAL_TRUE;
        }
        return isInfiniteLoop;
    }

}
