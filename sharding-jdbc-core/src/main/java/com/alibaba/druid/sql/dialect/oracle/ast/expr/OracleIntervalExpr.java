/*
 * Copyright 1999-2101 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.sql.dialect.oracle.ast.expr;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLExprImpl;
import com.alibaba.druid.sql.ast.expr.SQLLiteralExpr;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
public class OracleIntervalExpr extends SQLExprImpl implements SQLLiteralExpr, OracleExpr {
    
    private SQLExpr value;
    
    private OracleIntervalType type;
    
    private Integer precision;
    
    private Integer factionalSecondsPrecision;
    
    private OracleIntervalType toType;
    
    private Integer toFactionalSecondsPrecision;
    
    @Override
    protected void acceptInternal(final SQLASTVisitor visitor) {
        accept0((OracleASTVisitor) visitor);
    }
    
    @Override
    public void accept0(final OracleASTVisitor visitor) {
        visitor.visit(this);
        visitor.endVisit(this);
    }
}
