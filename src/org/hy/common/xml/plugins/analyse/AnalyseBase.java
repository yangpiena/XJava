package org.hy.common.xml.plugins.analyse;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hy.common.Busway;
import org.hy.common.Date;
import org.hy.common.Help;
import org.hy.common.MethodReflect;
import org.hy.common.Queue;
import org.hy.common.Return;
import org.hy.common.StringHelp;
import org.hy.common.app.Param;
import org.hy.common.file.FileHelp;
import org.hy.common.net.ClientSocket;
import org.hy.common.net.ClientSocketCluster;
import org.hy.common.net.data.CommunicationResponse;
import org.hy.common.thread.Job;
import org.hy.common.xml.XJSON;
import org.hy.common.xml.XJSONObject;
import org.hy.common.xml.XJava;
import org.hy.common.xml.XSQL;
import org.hy.common.xml.XSQLLog;
import org.hy.common.xml.annotation.Xjava;
import org.hy.common.xml.plugins.AppInitConfig;
import org.hy.common.xml.plugins.XSQLGroup;





/**
 * 分析服务的基础类
 *
 * @author      ZhengWei(HY)
 * @createDate  2015-12-15
 * @version     v1.0
 * @version     v2.0  2017-01-04  添加：查看XSQL对象执行错误的SQL语句
 * @version     v2.1  2017-01-06  添加：查看XSQL对象相关的触发器执行错误的SQL语句
 *                                添加：查看XSQL对象相关的触发器执行统计信息
 *              v3.0  2017-01-17  添加：集群重新加载XJava配置文件的功能
 *                                添加：集群执行对象方法
 *                                添加：查看集群服务列表
 *              v4.0  2017-01-22  添加：查看集群数据库访问量的概要统计数据
 *                                添加：查看集群数据库组合SQL访问量的概要统计数据 
 *                                添加：查看集群查看XSQL对象执行错误的SQL语句
 *              v5.0  2017-01-25  添加：跨域的单点登陆 和 集群的单点登陆功能
 *              v6.0  2017-03-01  添加：查看前缀匹配的对象列表页面，添加显示对象.toString()的信息。
 *                                     特殊情况1: 对于Java默认的toString()返回值不予显示。
 *                                     特殊情况2: 对于集合对象，不予显示。
 */
@Xjava
public class AnalyseBase
{
    
    /** 模板信息的缓存 */
    private final static Map<String ,String>          $TemplateCaches  = new Hashtable<String ,String>();
    
    /** 服务器启动时间 */
    private       static Date                         $ServerStartTime = null;
    
    
    
    public AnalyseBase()
    {
        if ( $ServerStartTime == null )
        {
            $ServerStartTime = new Date();
        }
    }
    
    
    
    /**
     * 验证登录
     * 
     * @author      ZhengWei(HY)
     * @createDate  2016-01-07
     * @version     v1.0
     *
     * @param  i_LogonPath       登录的URL。如：http://127.0.0.1:80/hy/../analyseObject  (可选：附加用户输入的参数)
     * @return
     */
    public String login(String i_LogonPath)
    {
        String v_Content = this.getTemplateLogon();
        
        return StringHelp.replaceAll(v_Content ,":LoginPath" ,i_LogonPath);
    }
    
    
    
    /**
     * 验证单点登录
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-25
     * @version     v1.0
     *
     * @param  i_RequestURL      请求的路径。如：http://127.0.0.1:80/hy/../analyseObject  (无用户附加的请求参数)
     * @param  i_LoginPath       登录的URL。如：http://127.0.0.1:80/hy/../analyseObject  (当用户有附加参数时，也一同带上)
     * @return
     */
    public String loginSSO(int i_ServerPort ,String i_RequestURL ,String i_LoginPath)
    {
        StringBuilder      v_Buffer  = new StringBuilder();
        String             v_Content = "<script type='text/javascript' src=':BasePath?SSOCallBack=getUSID&r=" + Math.random() + "'></script>";
        List<ClientSocket> v_Servers = Cluster.getClusters();
        
        // 给登陆的URL带上一个r参数
        String v_LoginPath = i_LoginPath;
        if ( v_LoginPath.indexOf("?") >= 0 )
        {
            if ( v_LoginPath.indexOf("&r=") < 0 && v_LoginPath.indexOf("?r=") < 0 )
            {
                v_LoginPath += "&r=" + Math.random();
            }
        }
        else
        {
            v_LoginPath += "?r=" + Math.random();
        }
        
        if ( Help.isNull(v_Servers) )
        {
            return this.login(v_LoginPath);
        }
        
        String v_RequestURL = i_RequestURL.split("//")[1].split("/")[0];
        v_RequestURL = StringHelp.replaceAll(i_RequestURL ,v_RequestURL ,":IPPort");
        
        for (ClientSocket v_Server : v_Servers)
        {
            v_Buffer.append(StringHelp.replaceAll(v_Content 
                                                 ,":BasePath" 
                                                 ,StringHelp.replaceAll(v_RequestURL ,":IPPort" ,v_Server.getHostName() + ":" + i_ServerPort)));
        }
        
        return StringHelp.replaceAll(this.getTemplateLogonSSO() 
                                    ,new String[]{":LoginPath" ,":Content"} 
                                    ,new String[]{v_LoginPath  ,v_Buffer.toString()});
    }
    
    
    
    /**
     * 获取数据库组合SQL访问量的概要统计数据（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2016-02-15
     * @version     v1.0
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseDB
     * @param  i_Cluster         是否为集群
     * @return
     */
    public String analyseDBGroup(String i_BasePath ,String i_ObjectValuePath ,boolean i_Cluster)
    {
        Map<String ,Object> v_Objs         = XJava.getObjects(XSQLGroup.class);
        StringBuilder       v_Buffer       = new StringBuilder();
        int                 v_Index        = 0;
        String              v_Content      = this.getTemplateShowTotalContent();
        long                v_RequestCount = 0;
        long                v_SuccessCount = 0;
        double              v_TotalTimeLen = 0;
        double              v_AvgTimeLen   = 0;
        Date                v_MaxExecTime  = null;
        long                v_NowTime      = 0L;
        AnalyseDBTotal      v_Total        = null;
        
        // 本机统计
        if ( !i_Cluster )
        {
            v_Total = this.analyseDBGroup_Total();
        }
        // 集群统计
        else
        {
            List<ClientSocket> v_Servers = Cluster.getClusters();
            v_Total = new AnalyseDBTotal();
            
            if ( !Help.isNull(v_Servers) )
            {
                Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseBase" ,"analyseDBGroup_Total");
                
                for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
                {
                    CommunicationResponse v_ResponseData = v_Item.getValue();
                    
                    if ( v_ResponseData.getResult() == 0 )
                    {
                        if ( v_ResponseData.getData() != null && v_ResponseData.getData() instanceof AnalyseDBTotal )
                        {
                            AnalyseDBTotal v_TempTotal = (AnalyseDBTotal)v_ResponseData.getData();
                            
                            v_Total.getRequestCount().putAll(v_TempTotal.getRequestCount());
                            v_Total.getSuccessCount().putAll(v_TempTotal.getSuccessCount());
                            v_Total.getMaxExecTime() .putAll(v_TempTotal.getMaxExecTime());
                            v_Total.getTotalTimeLen().putAll(v_TempTotal.getTotalTimeLen());
                        }
                    }
                }
            }
        }
        
        v_Objs    = Help.toSort(v_Objs);
        v_NowTime = new Date().getMinutes(-2).getTime();
        
        for (Map.Entry<String, Object> v_Item : v_Objs.entrySet())
        {
            if ( v_Item.getValue() != null )
            {
                // XSQLGroup v_GXSQL = (XSQLGroup)v_Item.getValue();
                
                v_RequestCount = v_Total.getRequestCount().getSumValue(v_Item.getKey());
                v_SuccessCount = v_Total.getSuccessCount().getSumValue(v_Item.getKey());
                v_TotalTimeLen = v_Total.getTotalTimeLen().getSumValue(v_Item.getKey());
                v_AvgTimeLen   = Help.round(Help.division(v_TotalTimeLen ,v_SuccessCount) ,2);
                v_MaxExecTime  = new Date(v_Total.getMaxExecTime().getMaxValue(v_Item.getKey()).longValue());
                
                v_Buffer.append(v_Content.replaceAll(":No"           ,String.valueOf(++v_Index))
                                         .replaceAll(":Name"         ,v_Item.getKey())
                                         .replaceAll(":RequestCount" ,String.valueOf(v_RequestCount))
                                         .replaceAll(":SuccessCount" ,String.valueOf(v_SuccessCount))
                                         .replaceAll(":FailCount"    ,String.valueOf(v_RequestCount - v_SuccessCount))
                                         .replaceAll(":ParamURL"     ,"#")
                                         .replaceAll(":ExecuteTime"  ,v_MaxExecTime == null || v_MaxExecTime.getTime() <= 0L ? "" : (v_MaxExecTime.getTime() >= v_NowTime ? v_MaxExecTime.getFull() : "<span style='color:gray;'>" + v_MaxExecTime.getFull() + "</span>"))
                                         .replaceAll(":SumTime"      ,Date.toTimeLen((long)v_TotalTimeLen))
                                         .replaceAll(":AvgTime"      ,String.valueOf(v_AvgTimeLen))
                               );
                
            }
        }
        
        v_RequestCount = v_Total.getRequestCount().getSumValue();
        v_SuccessCount = v_Total.getSuccessCount().getSumValue();
        v_TotalTimeLen = v_Total.getTotalTimeLen().getSumValue();
        v_AvgTimeLen   = Help.round(Help.division(v_TotalTimeLen ,v_SuccessCount) ,2);
        v_MaxExecTime  = new Date(v_Total.getMaxExecTime().getMaxValue().longValue());
        
        v_Buffer.append(v_Content.replaceAll(":No"           ,String.valueOf(++v_Index))
                                 .replaceAll(":Name"         ,"合计")
                                 .replaceAll(":RequestCount" ,String.valueOf(v_RequestCount))
                                 .replaceAll(":SuccessCount" ,String.valueOf(v_SuccessCount))
                                 .replaceAll(":FailCount"    ,String.valueOf(v_RequestCount - v_SuccessCount))
                                 .replaceAll(":ParamURL"     ,"#")
                                 .replaceAll(":ExecuteTime"  ,v_MaxExecTime == null || v_MaxExecTime.getTime() <= 0L ? "" : v_MaxExecTime.getFull())
                                 .replaceAll(":SumTime"      ,Date.toTimeLen((long)v_TotalTimeLen))
                                 .replaceAll(":AvgTime"      ,String.valueOf(v_AvgTimeLen))
                       );
        
        String v_Goto = StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB' style='color:#AA66CC'>查看SQL</a>";
        if ( i_Cluster )
        {
            v_Goto += StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB?type=Group' style='color:#AA66CC'>查看本机</a>";
        }
        else
        {
            v_Goto += StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB?type=Group&cluster=Y' style='color:#AA66CC'>查看集群</a>";
        }
        
        return StringHelp.replaceAll(this.getTemplateShowTotal()
                                    ,new String[]{":NameTitle"             ,":Title"                    ,":HttpBasePath" ,":Content"}
                                    ,new String[]{"组合SQL访问标识" + v_Goto ,"数据库组合SQL访问量的概要统计" ,i_BasePath      ,v_Buffer.toString()});
    }
    
    
    
    /**
     * 获取数据库访问量的概要统计数据（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2015-12-15
     * @version     v1.0
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseDB
     * @param  i_Cluster         是否为集群
     * @return
     */
    public String analyseDB(String i_BasePath ,String i_ObjectValuePath ,boolean i_Cluster)
    {
        Map<String ,Object> v_Objs            = XJava.getObjects(XSQL.class);
        StringBuilder       v_Buffer          = new StringBuilder();
        int                 v_Index           = 0;
        String              v_Content         = this.getTemplateShowTotalContent();
        String              v_OperateURL      = "";
        long                v_RequestCount    = 0;
        long                v_SuccessCount    = 0;
        long                v_TriggerReqCount = 0;
        long                v_TriggerSucCount = 0;
        long                v_TriggerFaiCount = 0;
        double              v_TotalTimeLen    = 0;
        double              v_AvgTimeLen      = 0;
        Date                v_MaxExecTime     = null;
        long                v_NowTime         = 0L;
        AnalyseDBTotal      v_Total           = null;
        
        // 本机统计
        if ( !i_Cluster )
        {
            v_Total = this.analyseDB_Total();
        }
        // 集群统计
        else
        {
            List<ClientSocket> v_Servers = Cluster.getClusters();
            v_Total = new AnalyseDBTotal();
            
            if ( !Help.isNull(v_Servers) )
            {
                Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseBase" ,"analyseDB_Total");
                
                for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
                {
                    CommunicationResponse v_ResponseData = v_Item.getValue();
                    
                    if ( v_ResponseData.getResult() == 0 )
                    {
                        if ( v_ResponseData.getData() != null && v_ResponseData.getData() instanceof AnalyseDBTotal )
                        {
                            AnalyseDBTotal v_TempTotal = (AnalyseDBTotal)v_ResponseData.getData();
                            
                            v_Total.getRequestCount()   .putAll(v_TempTotal.getRequestCount());
                            v_Total.getSuccessCount()   .putAll(v_TempTotal.getSuccessCount());
                            v_Total.getTriggerReqCount().putAll(v_TempTotal.getTriggerReqCount());
                            v_Total.getTriggerSucCount().putAll(v_TempTotal.getTriggerSucCount());
                            v_Total.getMaxExecTime()    .putAll(v_TempTotal.getMaxExecTime());
                            v_Total.getTotalTimeLen()   .putAll(v_TempTotal.getTotalTimeLen());
                        }
                    }
                }
            }
        }
        
        v_Objs    = Help.toSort(v_Objs);
        v_NowTime = new Date().getMinutes(-2).getTime();
        
        for (Map.Entry<String, Object> v_Item : v_Objs.entrySet())
        {
            if ( v_Item.getValue() != null )
            {
                XSQL v_XSQL = (XSQL)v_Item.getValue();
                
                v_RequestCount = v_Total.getRequestCount().getSumValue(v_Item.getKey());
                v_SuccessCount = v_Total.getSuccessCount().getSumValue(v_Item.getKey());
                v_TotalTimeLen = v_Total.getTotalTimeLen().getSumValue(v_Item.getKey());
                v_AvgTimeLen   = Help.round(Help.division(v_TotalTimeLen ,v_SuccessCount) ,2);
                v_MaxExecTime  = new Date(v_Total.getMaxExecTime().getMaxValue(v_Item.getKey()).longValue());
                
                if ( v_RequestCount > v_SuccessCount )
                {
                    v_OperateURL = i_ObjectValuePath + "?xsqloid=" + v_XSQL.getObjectID() + "&xsqlxid=" + v_Item.getKey();
                    
                    if ( i_Cluster )
                    {
                        v_OperateURL += "&cluster=Y"; 
                    }
                }
                else
                {
                    v_OperateURL = "#";
                }
               
                // 触发器的执行统计
                if ( v_XSQL.isTriggers() )
                {
                    v_TriggerReqCount = v_Total.getTriggerReqCount().getSumValue(v_Item.getKey());
                    v_TriggerSucCount = v_Total.getTriggerSucCount().getSumValue(v_Item.getKey());
                }
                else
                {
                    v_TriggerReqCount = 0;
                    v_TriggerSucCount = 0;
                }
                
                v_TriggerFaiCount = v_TriggerReqCount - v_TriggerSucCount;
                
                v_Buffer.append(v_Content.replaceAll(":No"           ,String.valueOf(++v_Index))
                                         .replaceAll(":Name"         ,v_Item.getKey())
                                         .replaceAll(":RequestCount" ,String.valueOf(v_RequestCount)                  + (v_TriggerReqCount > 0 ? "-T"+v_TriggerReqCount : ""))
                                         .replaceAll(":SuccessCount" ,String.valueOf(v_SuccessCount)                  + (v_TriggerReqCount > 0 ? "-T"+v_TriggerSucCount : ""))
                                         .replaceAll(":FailCount"    ,String.valueOf(v_RequestCount - v_SuccessCount) + (v_TriggerFaiCount > 0 ? "-T"+v_TriggerFaiCount : ""))
                                         .replaceAll(":ParamURL"     ,v_OperateURL)
                                         .replaceAll(":ExecuteTime"  ,v_MaxExecTime == null || v_MaxExecTime.getTime() <= 0L ? "" : (v_MaxExecTime.getTime() >= v_NowTime ? v_MaxExecTime.getFull() : "<span style='color:gray;'>" + v_MaxExecTime.getFull() + "</span>"))
                                         .replaceAll(":SumTime"      ,Date.toTimeLen((long)v_TotalTimeLen))
                                         .replaceAll(":AvgTime"      ,String.valueOf(v_AvgTimeLen))
                               );
            }
        }
        
        v_RequestCount = v_Total.getRequestCount().getSumValue();
        v_SuccessCount = v_Total.getSuccessCount().getSumValue();
        v_TotalTimeLen = v_Total.getTotalTimeLen().getSumValue();
        v_AvgTimeLen   = Help.round(Help.division(v_TotalTimeLen ,v_SuccessCount) ,2);
        v_MaxExecTime  = new Date(v_Total.getMaxExecTime().getMaxValue().longValue());
        
        v_Buffer.append(v_Content.replaceAll(":No"           ,String.valueOf(++v_Index))
                                 .replaceAll(":Name"         ,"合计")
                                 .replaceAll(":RequestCount" ,String.valueOf(v_RequestCount))
                                 .replaceAll(":SuccessCount" ,String.valueOf(v_SuccessCount))
                                 .replaceAll(":FailCount"    ,String.valueOf(v_RequestCount - v_SuccessCount))
                                 .replaceAll(":ParamURL"     ,"#")
                                 .replaceAll(":ExecuteTime"  ,v_MaxExecTime == null || v_MaxExecTime.getTime() <= 0L ? "" : v_MaxExecTime.getFull())
                                 .replaceAll(":SumTime"      ,Date.toTimeLen((long)v_TotalTimeLen))
                                 .replaceAll(":AvgTime"      ,String.valueOf(v_AvgTimeLen))
                       );
        
        String v_Goto = StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB?type=Group' style='color:#AA66CC'>查看SQL组</a>";
        if ( i_Cluster )
        {
            v_Goto += StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB' style='color:#AA66CC'>查看本机</a>";
        }
        else
        {
            v_Goto += StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseDB?cluster=Y' style='color:#AA66CC'>查看集群</a>";
        }
        
        return StringHelp.replaceAll(this.getTemplateShowTotal()
                                    ,new String[]{":NameTitle"          ,":Title"              ,":HttpBasePath" ,":Content"}
                                    ,new String[]{"SQL访问标识" + v_Goto ,"数据库访问量的概要统计" ,i_BasePath      ,v_Buffer.toString()});
    }
    
    
    
    /**
     * 获取数据库组合SQL访问量的概要统计数据
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-22
     * @version     v1.0
     *
     * @return
     */
    public AnalyseDBTotal analyseDBGroup_Total()
    {
        AnalyseDBTotal      v_Total = new AnalyseDBTotal();
        Map<String ,Object> v_Objs  = XJava.getObjects(XSQLGroup.class);
        
        for (Map.Entry<String, Object> v_Item : v_Objs.entrySet())
        {
            if ( v_Item.getValue() != null )
            {
                XSQLGroup v_XSQLGroup = (XSQLGroup)v_Item.getValue();
                
                v_Total.getRequestCount().put(v_Item.getKey() ,v_XSQLGroup.getRequestCount());
                v_Total.getSuccessCount().put(v_Item.getKey() ,v_XSQLGroup.getSuccessCount());
                v_Total.getMaxExecTime() .put(v_Item.getKey() ,v_XSQLGroup.getExecuteTime() == null ? 0L : v_XSQLGroup.getExecuteTime().getTime());
                v_Total.getTotalTimeLen().put(v_Item.getKey() ,v_XSQLGroup.getSuccessTimeLen());
            }
        }
        
        return v_Total;
    }
    
    
    
    /**
     * 获取数据库访问量的概要统计数据 
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-20
     * @version     v1.0
     *
     * @return
     */
    public AnalyseDBTotal analyseDB_Total()
    {
        AnalyseDBTotal      v_Total = new AnalyseDBTotal();
        Map<String ,Object> v_Objs  = XJava.getObjects(XSQL.class);
        
        for (Map.Entry<String, Object> v_Item : v_Objs.entrySet())
        {
            if ( v_Item.getValue() != null )
            {
                XSQL v_XSQL = (XSQL)v_Item.getValue();
                
                v_Total.getRequestCount().put(v_Item.getKey() ,v_XSQL.getRequestCount());
                v_Total.getSuccessCount().put(v_Item.getKey() ,v_XSQL.getSuccessCount());
                
                // 触发器的执行统计
                if ( v_XSQL.isTriggers() )
                {
                    v_Total.getTriggerReqCount().put(v_Item.getKey() ,v_XSQL.getTrigger().getRequestCount());
                    v_Total.getTriggerSucCount().put(v_Item.getKey() ,v_XSQL.getTrigger().getSuccessCount());
                }
                
                v_Total.getMaxExecTime() .put(v_Item.getKey() ,v_XSQL.getExecuteTime() == null ? 0L : v_XSQL.getExecuteTime().getTime());
                v_Total.getTotalTimeLen().put(v_Item.getKey() ,v_XSQL.getSuccessTimeLen());
            }
        }
        
        return v_Total;
    }
    
    
    
    /**
     * 功能1. 查看XSQL对象执行错误的SQL语句（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-04
     * @version     v1.0
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseDB
     * @param  i_XSQLOID         XSQL的惟一标识getObjectID()
     * @param  i_XSQLXID         XSQL对象的XID
     * @param  i_Cluster         是否为集群
     * @return
     */
    @SuppressWarnings("unchecked")
    public String analyseDBError(String i_BasePath ,String i_ObjectValuePath ,String i_XSQLOID ,String i_XSQLXID ,boolean i_Cluster)
    {
        if ( Help.isNull(i_XSQLOID) || Help.isNull(i_XSQLXID) )
        {
            return "";
        }
        
        try
        {
            List<XSQLLog> v_ErrorLogs = null;
            
            // 本机统计
            if ( !i_Cluster )
            {
                v_ErrorLogs = this.analyseDBError_Total(i_XSQLOID ,i_XSQLXID);
            }
            // 集群统计
            else
            {
                List<ClientSocket> v_Servers = Cluster.getClusters();
                v_ErrorLogs = new ArrayList<XSQLLog>();
                
                if ( !Help.isNull(v_Servers) )
                {
                    Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseBase" ,"analyseDBError_Total" ,new Object[]{i_XSQLOID ,i_XSQLXID});
                    
                    for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
                    {
                        CommunicationResponse v_ResponseData = v_Item.getValue();
                        
                        if ( v_ResponseData.getResult() == 0 )
                        {
                            if ( v_ResponseData.getData() != null && v_ResponseData.getData() instanceof List )
                            {
                                v_ErrorLogs.addAll((List<XSQLLog>)(v_ResponseData.getData()));
                            }
                        }
                    }
                }
            }
            
            String v_Content      = "";
            String v_OperateURL   = "#";
            String v_OperateTitle = "";
            XJSON  v_XJSON        = new XJSON();
            v_XJSON.setReturnNVL(true);
            v_XJSON.setAccuracy(true);
            
            XJSONObject v_Ret = v_XJSON.parser(v_ErrorLogs);
            if ( null != v_Ret )
            {
                v_Content = v_Ret.toJSONString();
            }
            else
            {
                v_Content = "{}";
            }
            
            return StringHelp.replaceAll(this.getTemplateShowObject() 
                                        ,new String[]{":HttpBasePath" ,":TitleInfo"      ,":XJavaObjectID" ,":Content" ,":OperateURL1" ,":OperateTitle1" ,":OperateURL2" ,":OperateTitle2" ,":OperateURL3" ,":OperateTitle3"} 
                                        ,new String[]{i_BasePath      ,"执行异常的SQL语句" ,i_XSQLXID        ,v_Content  ,v_OperateURL   ,v_OperateTitle   ,v_OperateURL   ,v_OperateTitle   ,v_OperateURL   ,v_OperateTitle});
        }
        catch (Exception exce)
        {
            return exce.toString();
        }
    }
    
    
    
    /**
     * 查看XSQL对象执行错误的SQL语句
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-22
     * @version     v1.0
     *
     * @param  i_XSQLOID         XSQL的惟一标识getObjectID()
     * @param  i_XSQLXID         XSQL对象的XID
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<XSQLLog> analyseDBError_Total(String i_XSQLOID ,String i_XSQLXID)
    {
        XSQL          v_XSQLMaster = XJava.getXSQL(i_XSQLXID);
        List<XSQLLog> v_ErrorLogs  = new ArrayList<XSQLLog>();
        
        Busway<XSQLLog> v_SQLBuswayError = (Busway<XSQLLog>)XJava.getObject("$SQLBuswayError");
        if ( v_SQLBuswayError == null || v_SQLBuswayError.size() <= 0 )
        {
            return v_ErrorLogs;
        }
        
        for (Object v_Item : v_SQLBuswayError.getArray())
        {
            XSQLLog v_XSQLLog = (XSQLLog)v_Item;
            if ( v_XSQLLog != null )
            {
                if ( i_XSQLOID.equals(v_XSQLLog.getOid()) )
                {
                    v_ErrorLogs.add(v_XSQLLog);
                }
                else if ( v_XSQLMaster.isTriggers() )
                {
                    // 同时添加主XSQL相关的触发器的异常SQL信息  ZengWei(HY) Add 2017-01-06
                    for (XSQL v_XSQL : v_XSQLMaster.getTrigger().getXsqls())
                    {
                        if ( v_XSQL.getObjectID().equals(v_XSQLLog.getOid()) )
                        {
                            v_ErrorLogs.add(v_XSQLLog);
                        }
                    }
                }
            }
        }
        
        return v_ErrorLogs;
    }
    
    
    
    /**
     * 功能1. 查看对象信息
     * 功能2. 执行对象方法（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2015-12-16
     * @version     v1.0
     *              v2.0  2017-01-17  添加：集群顺次执行对象方法的功能
     *              v3.0  2017-01-20  添加：集群同时执行对象方法的功能（并发）
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseObject
     * @param  i_XJavaObjectID   对象标识ID 
     * @param  i_CallMethod      对象方法的全名称（可为空）
     * @param  i_Cluster         是否为集群
     * @param  i_SameTime        是否为同时执行（并发操作）
     * @return
     */
    public String analyseObject(String i_BasePath ,String i_ObjectValuePath ,String i_XJavaObjectID ,String i_CallMethod ,boolean i_Cluster ,boolean i_SameTime)
    {
        if ( Help.isNull(i_XJavaObjectID) )
        {
            return "";
        }
        
        try
        {
            Object v_Object = XJava.getObject(i_XJavaObjectID);
            if ( v_Object == null )
            {
                return "";
            }
            
            String v_Content       = "";
            String v_OperateURL1   = "#";
            String v_OperateTitle1 = "";
            String v_OperateURL2   = "#";
            String v_OperateTitle2 = "";
            String v_OperateURL3   = "#";
            String v_OperateTitle3 = "";
            XJSON  v_XJSON         = new XJSON();
            v_XJSON.setReturnNVL(true);
            v_XJSON.setAccuracy(true);
            
            // 功能1. 查看对象信息
            if ( Help.isNull(i_CallMethod) )
            {
                XJSONObject v_Ret = v_XJSON.parser(v_Object);
                if ( null != v_Ret )
                {
                    v_Content = v_Ret.toJSONString();
                }
                else
                {
                    v_Content = "{}";
                }
                
                if ( v_Object.getClass() == Job.class )
                {
                    Job v_Job = (Job)v_Object;
                    v_OperateURL1   = i_ObjectValuePath + "?xid=" + v_Job.getXjavaID() + "&call=" + v_Job.getMethodName();
                    v_OperateTitle1 = "执行任务";
                    v_OperateURL2   = v_OperateURL1 + "&cluster=Y";
                    v_OperateTitle2 = "集群顺次执行任务";
                    v_OperateURL3   = v_OperateURL2 + "&sameTime=Y";
                    v_OperateTitle3 = "集群同时执行任务";
                }
                else if ( v_Object.getClass() == XSQLGroup.class )
                {
                    v_OperateURL1   = i_ObjectValuePath + "?xid=" + i_XJavaObjectID + "&call=executes";
                    v_OperateTitle1 = "执行SQL组";
                    v_OperateURL2   = v_OperateURL1 + "&cluster=Y";
                    v_OperateTitle2 = "集群顺次执行SQL组";
                    v_OperateURL3   = v_OperateURL2 + "&sameTime=Y";
                    v_OperateTitle3 = "集群同时执行SQL组";
                }
                
                return StringHelp.replaceAll(this.getTemplateShowObject() 
                                            ,new String[]{":HttpBasePath" ,":TitleInfo"  ,":XJavaObjectID" ,":Content" ,":OperateURL1" ,":OperateTitle1" ,":OperateURL2" ,":OperateTitle2" ,":OperateURL3" ,":OperateTitle3"} 
                                            ,new String[]{i_BasePath      ,"对象信息"     ,i_XJavaObjectID  ,v_Content  ,v_OperateURL1  ,v_OperateTitle1  ,v_OperateURL2  ,v_OperateTitle2  ,v_OperateURL3  ,v_OperateTitle3});
            }
            // 功能2. 执行对象方法 
            else
            {
                Return<String> v_RetInfo = this.analyseObject_Execute(i_XJavaObjectID ,i_CallMethod ,i_Cluster ,i_SameTime);
                v_Content = v_RetInfo.paramStr;
                
                if ( !i_Cluster )
                {
                    List<Method> v_Methods = MethodReflect.getMethodsIgnoreCase(v_Object.getClass() ,i_CallMethod ,0);
                    return StringHelp.replaceAll(this.getTemplateShowObject() 
                                                ,new String[]{":HttpBasePath" ,":TitleInfo"    ,":XJavaObjectID"                                           ,":Content" ,":OperateURL1" ,":OperateTitle1" ,":OperateURL2" ,":OperateTitle2" ,":OperateURL3" ,":OperateTitle3"} 
                                                ,new String[]{i_BasePath      ,"对象方法执行结果" ,i_XJavaObjectID + "." + v_Methods.get(0).getName() + "()"  ,v_Content  ,""});
                }
                else
                {
                    return v_Content;
                }
            }
        }
        catch (Exception exce)
        {
            return exce.toString();
        }
    }
    
    
    
    /**
     * 执行对象方法（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-17
     * @version     v1.0
     *
     * @param  i_XJavaObjectID   对象标识ID 
     * @param  i_CallMethod      对象方法的全名称（可为空）
     * @param  i_Cluster         是否为集群
     * @param  i_SameTime        是否为同时执行（并发操作）
     * @return
     */
    @SuppressWarnings("unchecked")
    public Return<String> analyseObject_Execute(String i_XJavaObjectID ,String i_CallMethod ,boolean i_Cluster ,boolean i_SameTime)
    {
        Return<String> v_RetInfo = new Return<String>();
        Object         v_Object  = XJava.getObject(i_XJavaObjectID);
        
        if ( v_Object == null )
        {
            return v_RetInfo.paramStr("XID is not exists.");
        }
        
        List<Method> v_Methods = MethodReflect.getMethodsIgnoreCase(v_Object.getClass() ,i_CallMethod ,0);
        if ( Help.isNull(v_Methods) )
        {
            return v_RetInfo.paramStr("Can not find method [" + i_XJavaObjectID + "." + i_CallMethod + "()]!");
        }
        
        // 本机重新加载
        if ( !i_Cluster )
        {
            String v_Content = "";
            XJSON  v_XJSON   = new XJSON();
            v_XJSON.setReturnNVL(true);
            v_XJSON.setAccuracy(true);
            
            try
            {
                
                XSQLGroup v_GXSQ   = null;
                boolean   v_OldLog = false; 
                if ( XSQLGroup.class.equals(v_Object.getClass()) )
                {
                    // 当为XSQL组时，自动打开日志模式
                    v_GXSQ = (XSQLGroup)v_Object;
                    v_OldLog = v_GXSQ.isLog();
                    v_GXSQ.setLog(true);
                }
                
                Object v_CallRet = v_Methods.get(0).invoke(v_Object);
                
                if ( v_GXSQ != null )
                {
                    v_GXSQ.setLog(v_OldLog);
                }
                
                if ( v_CallRet != null )
                {
                    XJSONObject v_Ret = v_XJSON.parser(v_CallRet);
                    if ( null != v_Ret )
                    {
                        v_Content = v_Ret.toJSONString();
                    }
                    else
                    {
                        v_Content = "{\"return\":\"" + v_CallRet.toString() + "\"}";
                    }
                }
                else if ( null != v_Methods.get(0).getReturnType()
                       && "void".equals(v_Methods.get(0).getReturnType().getName()) )
                {
                    v_Content = "{\"return\":\"void\"}";
                }
                else
                {
                    v_Content = "{\"return\":\"null\"}";
                }
    
                return v_RetInfo.paramStr(v_Content).set(true);
            }
            catch (Exception exce)
            {
                return v_RetInfo.paramStr(exce.toString());
            }
        }
        // 集群重新加载
        else
        {
            long                                     v_StartTime        = Date.getNowTime().getTime();
            StringBuilder                            v_Ret              = new StringBuilder();
            Map<ClientSocket ,CommunicationResponse> v_ClusterResponses = null;
            
            if ( i_SameTime )
            {
                v_ClusterResponses = ClientSocketCluster.sendCommands(Cluster.getClusters() ,Cluster.getClusterTimeout() ,"AnalyseBase" ,"analyseObject_Execute" ,new Object[]{i_XJavaObjectID ,i_CallMethod ,false ,false});
            }
            else
            {
                v_ClusterResponses = ClientSocketCluster.sendCommands(Cluster.getClusters()                           ,"AnalyseBase" ,"analyseObject_Execute" ,new Object[]{i_XJavaObjectID ,i_CallMethod ,false ,false});
            }
            
            v_Ret.append("总体用时：").append(Date.toTimeLen(Date.getNowTime().getTime() - v_StartTime)).append("<br><br>");
            
            // 处理结果
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ClusterResponses.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                
                v_ClusterResponses.put(v_Item.getKey() ,v_ResponseData);
                
                v_Ret.append(v_ResponseData.getEndTime().getFullMilli()).append("：").append(v_Item.getKey().getHostName()).append(" execute ");
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() == null || !(v_ResponseData.getData() instanceof Return) )
                    {
                        v_Ret.append("is Error.");
                    }
                    else
                    {
                        Return<String> v_ExecRet = (Return<String>)v_ResponseData.getData();
                        
                        if ( v_ExecRet.booleanValue() )
                        {
                            v_Ret.append("is OK.");
                        }
                        else
                        {
                            v_Ret.append("is Error(").append(v_ExecRet.paramStr).append(").");
                        }
                    }
                }
                else
                {
                    v_Ret.append("is Error(").append(v_ResponseData.getResult()).append(").");
                }
                v_Ret.append("<br>");
            }
            
            return v_RetInfo.paramStr(v_Ret.toString());
        }
    }
    
    
    
    /**
     * 功能1. 查看前缀匹配的对象列表
     * 
     * @author      ZhengWei(HY)
     * @createDate  2016-01-06
     * @version     v1.0
     *              v2.0  2017-03-01  添加：显示对象.toString()的信息。
     *                                     特殊情况1: 对于Java默认的toString()返回值不予显示。
     *                                     特殊情况2: 对于集合对象，不予显示。
     *
     * @param  i_BasePath        服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ObjectValuePath 对象值的详情URL。如：http://127.0.0.1:80/hy/../analyseObject
     * @param  i_XIDPrefix       对象标识符的前缀(区分大小写)
     * @return
     */
    public String analyseObjects(String i_BasePath ,String i_ObjectValuePath ,String i_XIDPrefix)
    {
        Map<String ,Object>  v_Objects      = (Map<String ,Object>)XJava.getObjects(i_XIDPrefix);
        StringBuilder        v_Buffer       = new StringBuilder();
        int                  v_Index        = 0;
        String               v_Content      = this.getTemplateShowObjectsContent();
        
        v_Objects = Help.toSort(v_Objects);
        
        for (Entry<String, Object> v_Item : v_Objects.entrySet())
        {
            String v_Info = "";
            
            if ( v_Item.getValue() != null )
            {
                v_Info = "";
                
                if ( MethodReflect.isExtendImplement(v_Item.getValue() ,Set.class)
                  || MethodReflect.isExtendImplement(v_Item.getValue() ,Map.class)
                  || MethodReflect.isExtendImplement(v_Item.getValue() ,List.class)
                  || MethodReflect.isExtendImplement(v_Item.getValue() ,Queue.class) )
                {
                    // 对于集合对象，不予显示
                    v_Info = "";
                }
                else
                {
                    v_Info = Help.NVL(v_Item.getValue().toString());
                    
                    if ( v_Info.startsWith(v_Item.getValue().getClass().getName()) )
                    {
                        // 对于默认的toString()返回值不予显示
                        v_Info = "";
                    }
                }
            }
            
            v_Buffer.append(StringHelp.replaceAll(v_Content 
                                                 ,new String[]{":No" 
                                                              ,":Name" 
                                                              ,":Info"
                                                              ,":OperateURL" 
                                                              ,":OperateTitle"} 
                                                 ,new String[]{String.valueOf(++v_Index)
                                                              ,v_Item.getKey()
                                                              ,v_Info
                                                              ,i_ObjectValuePath + "?xid=" + v_Item.getKey()
                                                              ,"查看详情"
                                                              })
                           );
        }
        
        return StringHelp.replaceAll(this.getTemplateShowObjects()
                                    ,new String[]{":Title"  ,":Column01Title" ,":Column02Title"  ,":HttpBasePath" ,":Content"}
                                    ,new String[]{"对象列表" ,"对象标识"         ,"对象.toString()" ,i_BasePath      ,v_Buffer.toString()});
    }
    
    
    
    /**
     * 功能1：查看XJava配置文件列表
     * 功能2：重新加载XJava配置文件（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2016-01-04
     * @version     v1.0
     *              v2.0  2017-01-17  添加：集群重新加载XJava配置文件的功能
     *
     * @param  i_BasePath       服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ReLoadPath     重新加载的URL。如：http://127.0.0.1:80/hy/../analyseObject
     * @param  i_XFile          XJava配置文件名称（可为空）
     * @param  i_Cluster        是否为集群
     * @return
     */
    @SuppressWarnings("unchecked")
    public String analyseXFile(String i_BasePath ,String i_ReLoadPath ,String i_XFile ,boolean i_Cluster)
    {
        Map<String ,Object>  v_XFileNames   = (Map<String ,Object>)XJava.getObject(AppInitConfig.$XFileNames_XID);
        StringBuilder        v_Buffer       = new StringBuilder();
        int                  v_Index        = 0;
        String               v_Content      = this.getTemplateShowXFilesContent();
        
        if ( Help.isNull(i_XFile) )
        {
            for (String v_XFile : v_XFileNames.keySet())
            {
                if ( !Help.isNull(v_XFile) )
                {
                    v_Buffer.append(StringHelp.replaceAll(v_Content 
                                                         ,new String[]{":No" 
                                                                      ,":Name" 
                                                                      ,":OperateURL1" 
                                                                      ,":OperateTitle1"
                                                                      ,":OperateURL2" 
                                                                      ,":OperateTitle2"} 
                                                         ,new String[]{String.valueOf(++v_Index)
                                                                      ,v_XFile
                                                                      ,i_ReLoadPath + "?xfile=" + v_XFile
                                                                      ,"重新加载"
                                                                      ,i_ReLoadPath + "?xfile=" + v_XFile + "&cluster=Y"
                                                                      ,"集群重新加载"
                                                                      })
                                   );
                }
            }
            
            String v_Goto = StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseObject?cluster=Y' style='color:#AA66CC'>查看集群服务</a>";
            
            return StringHelp.replaceAll(this.getTemplateShowXFiles()
                                        ,new String[]{":Title"          ,":Column01Title"        ,":HttpBasePath" ,":Content"}
                                        ,new String[]{"XJava配置文件列表" ,"XJava配置文件" + v_Goto ,i_BasePath      ,v_Buffer.toString()});
        }
        else
        {
            return this.analyseXFile_Reload(i_XFile ,i_Cluster);
        }
    }
    
    
    
    /**
     * 重新加载XJava配置文件（支持集群）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-17
     * @version     v1.0
     *
     * @param i_XFile    XJava配置文件名称
     * @param i_Cluster  是否为集群
     * @return
     */
    @SuppressWarnings("unchecked")
    public String analyseXFile_Reload(String i_XFile ,boolean i_Cluster)
    {
        Map<String ,Object> v_XFileNames = (Map<String ,Object>)XJava.getObject(AppInitConfig.$XFileNames_XID);
        
        if ( v_XFileNames.containsKey(i_XFile) )
        {
            // 本机重新加载
            if ( !i_Cluster )
            {
                AppInitConfig v_AConfig  = (AppInitConfig)v_XFileNames.get(i_XFile);
                File          v_XFileObj = new File(Help.getWebINFPath() + i_XFile);
                
                if ( v_XFileObj.exists() && v_XFileObj.isFile() )
                {
                    v_AConfig.initW(i_XFile ,Help.getWebINFPath());
                }
                else
                {
                    v_AConfig.init(i_XFile);
                }
                
                return Date.getNowTime().getFullMilli() + ": Has completed re loading, please check the console log.";
            }
            // 集群重新加载 
            else
            {
                StringBuilder v_Ret = new StringBuilder();
                for (ClientSocket v_Client : Cluster.getClusters())
                {
                    CommunicationResponse v_ResponseData = v_Client.sendCommand("AnalyseBase" ,"analyseXFile_Reload" ,new Object[]{i_XFile ,false});
                    
                    v_Ret.append(Date.getNowTime().getFullMilli()).append("：").append(v_Client.getHostName()).append(" reload ");
                    v_Ret.append(v_ResponseData.getResult() == 0 ? "OK." : "Error(" + v_ResponseData.getResult() + ").").append("<br>");
                }
                
                return v_Ret.toString();
            }
        }
        else
        {
            return Date.getNowTime().getFullMilli() + ": Configuration file not found.";
        }
    }
    
    
    
    /**
     * 功能1：查看集群服务列表
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-18
     * @version     v1.0
     *
     * @param  i_BasePath       服务请求根路径。如：http://127.0.0.1:80/hy
     * @param  i_ReLoadPath     重新加载的URL。如：http://127.0.0.1:80/hy/../analyseObject
     * @return
     */
    @SuppressWarnings("unchecked")
    public String analyseCluster(String i_BasePath ,String i_ReLoadPath)
    {
        List<ClientSocket>   v_Servers      = Cluster.getClusters();
        StringBuilder        v_Buffer       = new StringBuilder();
        int                  v_Index        = 0;
        String               v_Content      = this.getTemplateShowClusterContent();
        
        if ( !Help.isNull(v_Servers) )
        {
            Map<ClientSocket ,CommunicationResponse> v_ResponseDatas = ClientSocketCluster.sendCommands(v_Servers ,Cluster.getClusterTimeout() ,"AnalyseBase" ,"analyseCluster_Info");
            
            for (Map.Entry<ClientSocket ,CommunicationResponse> v_Item : v_ResponseDatas.entrySet())
            {
                CommunicationResponse v_ResponseData = v_Item.getValue();
                String                v_StartTime    = "-";
                String                v_ServerStatus = "异常";
                
                if ( v_ResponseData.getResult() == 0 )
                {
                    if ( v_ResponseData.getData() != null && v_ResponseData.getData() instanceof Return )
                    {
                        v_StartTime    = ((Return<Date>)v_ResponseData.getData()).paramObj.getFullMilli();
                        v_ServerStatus = "正常";
                    }
                }
                
                v_Buffer.append(StringHelp.replaceAll(v_Content 
                                                     ,new String[]{":No" 
                                                                  ,":Name" 
                                                                  ,":StartTime" 
                                                                  ,":ServerStatus"} 
                                                     ,new String[]{String.valueOf(++v_Index)
                                                                  ,v_Item.getKey().getHostName()
                                                                  ,v_StartTime
                                                                  ,v_ServerStatus
                                                                  })
                               );
            }
            
            String v_Goto = StringHelp.lpad("" ,4 ,"&nbsp;") + "<a href='analyseObject' style='color:#AA66CC'>查看XJava配置</a>";
            
            return StringHelp.replaceAll(this.getTemplateShowCluster()
                                        ,new String[]{":Title"     ,":Column01Title"   ,":HttpBasePath" ,":Content"}
                                        ,new String[]{"集群服务列表" ,"集群服务" + v_Goto ,i_BasePath      ,v_Buffer.toString()});
        }
        else
        {
            return "No config cluster.";
        }
    }
    
    
    
    /**
     * 获取服务信息（如启动时间等）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-01-18
     * @version     v1.0
     *
     * @return
     */
    public Return<Date> analyseCluster_Info()
    {
        Return<Date> v_Ret = new Return<Date>(true);
        
        return v_Ret.paramObj($ServerStartTime);
    }
    
    
    
    private String getTemplateLogon()
    {
        return this.getTemplateContent("template.login.html");
    }
    
    
    
    private String getTemplateLogonSSO()
    {
        return this.getTemplateContent("template.loginSSO.html");
    }
    
    
    
    private String getTemplateShowTotal()
    {
        return this.getTemplateContent("template.showTotal.html");
    }
    
    
    
    private String getTemplateShowTotalContent()
    {
        return this.getTemplateContent("template.showTotalContent.html");
    }
    
    
    
    private String getTemplateShowObject()
    {
        return this.getTemplateContent("template.showObject.html");
    }
    
    
    
    private String getTemplateShowObjects()
    {
        return this.getTemplateContent("template.showObjects.html");
    }
    
    
    
    private String getTemplateShowObjectsContent()
    {
        return this.getTemplateContent("template.showObjectsContent.html");
    }
    
    
    
    private String getTemplateShowXFiles()
    {
        return this.getTemplateContent("template.showXFiles.html");
    }
    
    
    
    private String getTemplateShowXFilesContent()
    {
        return this.getTemplateContent("template.showXFilesContent.html");
    }
    
    
    
    private String getTemplateShowCluster()
    {
        return this.getTemplateContent("template.showCluster.html");
    }
    
    
    
    private String getTemplateShowClusterContent()
    {
        return this.getTemplateContent("template.showClusterContent.html");
    }
    
    
    
    /**
     * 获取模板内容（有缓存机制）
     * 
     * @author      ZhengWei(HY)
     * @createDate  2015-12-15
     * @version     v1.0
     *
     * @param i_TemplateName  模板名称
     * @return
     */
    protected String getTemplateContent(String i_TemplateName)
    {
        if ( $TemplateCaches.containsKey(i_TemplateName) )
        {
            return $TemplateCaches.get(i_TemplateName);
        }
        
        String v_Content = "";
        
        try
        {
            v_Content = this.getFileContent(i_TemplateName);
            $TemplateCaches.put(i_TemplateName ,v_Content);
        }
        catch (Exception exce)
        {
            exce.printStackTrace();
        }
        
        return v_Content;
    }
    
    
    
    /**
     * 获取文件内容
     * 
     * @author      ZhengWei(HY)
     * @createDate  2015-12-15
     * @version     v1.0
     *
     * @param i_FileName  文件名称(无须文件路径)。此文件应在同级目录中保存
     * @return
     * @throws Exception
     */
    protected String getFileContent(String i_FileName) throws Exception
    {
        FileHelp    v_FileHelp    = new FileHelp();
        String      v_PackageName = "org.hy.common.xml.plugins.analyse".replaceAll("\\." ,"/");
        InputStream v_InputStream = this.getClass().getResourceAsStream("/" + v_PackageName + "/" + i_FileName);
        
        return v_FileHelp.getContent(v_InputStream ,"UTF-8");
    }
    
    
    
    /**
     * 获取参数Param对象
     * 
     * @author      ZhengWei(HY)
     * @createDate  2015-12-15
     * @version     v1.0
     *
     * @param i_XJavaID
     * @return
     */
    protected Param getParam(String i_XJavaID)
    {
        return (Param)XJava.getObject(i_XJavaID);
    }
    
}
