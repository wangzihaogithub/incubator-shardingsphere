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
package com.alibaba.druid.sql.dialect.mysql.ast;

import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.statement.SQLTableConstraint;
import com.alibaba.druid.sql.ast.statement.SQLUnique;
import com.alibaba.druid.sql.ast.statement.SQLUniqueConstraint;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitor;
import com.alibaba.druid.sql.visitor.SQLASTVisitor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MySqlKey extends SQLUnique implements SQLUniqueConstraint, SQLTableConstraint {
    
    private SQLName indexName;
    
    private String  indexType;
    
    private boolean hasConstraint;
    
    @Override
    protected void acceptInternal(final SQLASTVisitor visitor) {
        if (visitor instanceof MySqlASTVisitor) {
            if (visitor.visit(this)) {
                acceptChild(visitor, getName());
                acceptChild(visitor, getColumns());
                acceptChild(visitor, indexName);
            }
            visitor.endVisit(this);
        }
    }
}
