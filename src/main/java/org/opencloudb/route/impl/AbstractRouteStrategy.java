package org.opencloudb.route.impl;

import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import org.apache.log4j.Logger;
import org.opencloudb.MycatSystem;
import org.opencloudb.cache.LayerCachePool;
import org.opencloudb.config.model.SchemaConfig;
import org.opencloudb.config.model.SystemConfig;
import org.opencloudb.net.FrontSession;
import org.opencloudb.route.RouteResultset;
import org.opencloudb.route.RouteStrategy;
import org.opencloudb.route.util.RouterUtil;
import org.opencloudb.server.parser.ServerParse;

public abstract class AbstractRouteStrategy implements RouteStrategy {
	private static final Logger LOGGER = Logger.getLogger(AbstractRouteStrategy.class);

	@Override
	public RouteResultset route(SystemConfig sysConfig, SchemaConfig schema,int sqlType, String origSQL, 
			String charset, FrontSession session, LayerCachePool cachePool) throws SQLNonTransientException {
		if (RouterUtil.processWithMycatSeq(sysConfig,schema, sqlType, origSQL, charset,session, cachePool) || 
				(sqlType == ServerParse.INSERT && RouterUtil.processInsert(sysConfig,schema,sqlType,origSQL,charset,session,cachePool))) {
				return null;
			}

		// user handler
		String stmt = MycatSystem.getInstance().getSqlInterceptor().interceptSQL(origSQL, sqlType);
		
		if (origSQL != stmt && LOGGER.isDebugEnabled()) {
			LOGGER.debug("sql intercepted to " + stmt + " from " + origSQL);
		}
		if (schema.isCheckSQLSchema()) {
			stmt = RouterUtil.removeSchema(stmt, schema.getName());
		}
		RouteResultset rrs = new RouteResultset(stmt, sqlType);
		
		// check if there is sharding in schema
		if (schema.isNoSharding()) {
			return RouterUtil.routeToSingleNode(rrs, schema.getDataNode(), stmt);
		}
		RouteResultset returnedSet=routeSystemInfo(schema, sqlType, stmt, rrs);
		if(returnedSet==null){
			return routeNormalSqlWithAST(schema, stmt, rrs, charset, cachePool);
		}
		return returnedSet;
	}
	
	/**
	 * 通过解析AST语法树类来寻找路由
	 * @param schema
	 * @param stmt
	 * @param rrs
	 * @param charset
	 * @param cachePool
	 * @return
	 * @throws SQLNonTransientException
	 */
	public abstract RouteResultset routeNormalSqlWithAST(SchemaConfig schema,String stmt,RouteResultset rrs,String charset,LayerCachePool cachePool) throws SQLNonTransientException;
	
	/**
	 * 
	 * @param schema
	 * @param sqlType
	 * @param stmt
	 * @param rrs
	 * @return
	 * @throws SQLSyntaxErrorException
	 */
	public abstract RouteResultset routeSystemInfo(SchemaConfig schema,int sqlType,String stmt,RouteResultset rrs) throws SQLSyntaxErrorException;
	
	/**
	 * show  之类的语句
	 * @param schema
	 * @param rrs
	 * @param stmt
	 * @return
	 * @throws SQLSyntaxErrorException
	 */
	public abstract RouteResultset analyseShowSQL(SchemaConfig schema,RouteResultset rrs, String stmt) throws SQLNonTransientException;

}
