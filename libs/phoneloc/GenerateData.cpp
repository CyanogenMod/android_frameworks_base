/* Copyright (C) 2013 The MoKee Open Source Project
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

#include <stdio.h>
#include <string.h>
#include "Global.h"

#define LINE_BUFFER_SIZE 256

char * ChangeFileExt(const char * fn, const char * fext)
{
	int l=strlen(fn);
	int le=strlen(fext);
    int i;
	for( i=l-1; fn[i]!='.' && fn[i]!='\\' && fn[i]!='/' && fn[i]!=':' && i>=0; i--) ;
	char * fnext;
	//如果没扩展名
	if(i<=0||fn[i]=='\\'||fn[i]=='/'||fn[i]==':')
	{
		fnext=new char[l+le+2];
		strcpy(fnext,fn);
		fnext[l]='.';
		l++;
		strcpy(fnext+l,fext);
	}
	else
	{
		l=i+1;
		fnext=new char[l+le+1];
		strcpy(fnext,fn);
		strcpy(fnext+l,fext);
	}
	//申请新文件名的存储空间
	return fnext;
}

//在字符串链表中搜索字符串,返回节点指针,若无则返回NULL
inline StringNode * FindString(StringNode * st, const char * str)
{
	for(StringNode * ps=st; ps!=NULL; ps=ps->next)
	{
		if(strcmp(ps->value,str)==0)
			return ps;
	}
	return NULL;
}

//文本数据 -> 二进制数据文件
int MpDataConvert(const char * fnin, const char * fnout)
{
	FILE * fpin=fopen(fnin,"rb");     //输入文件
	if(!fpin)
	{
		printf("打开文件失败!\n");
		return 1;
	}

	printf("正在导入文件 [%s] 到 [%s] ... ",fnin,fnout);

	StringNode * stringTable;
	IndexNode * indexTable;
	StringNode * ps;
	IndexNode * p;
	int numLast;   //上一行的号码
	int numRead;   //当前读取的号码
	char * addrRead=new char[LINE_BUFFER_SIZE];
    int tmp; 
    unsigned short cityCode;// = new char [5];
	int sfCount=0; //源文件记录计数
	bool isFirst=true;
	while(!feof(fpin))
	{
		fscanf(fpin,"%d,%[^,],%d",&numRead,addrRead,&tmp); sfCount++;
		cityCode = tmp;
		//首记录处理
		if(isFirst)
		{
			ps=stringTable=new StringNode(addrRead,cityCode);
			ps->next=NULL;
			p=indexTable=new IndexNode(numRead,0,ps);
			p->next=NULL;
			isFirst=false;
			//保存本次读取的号码以便读下一行时用到
			numLast=numRead;
			continue;
		}
		//如果地址未变
		if(strcmp(p->Address->value,addrRead)==0)
		{
			//保存本次读取的号码以便读下一行时用到
			numLast=numRead;
			continue;
		}
		//如果地址变了
		else
		{
			//完成前一条记录
			p->NumEnd=numLast;
			//开始新记录
			StringNode * s=FindString(stringTable,addrRead);
			if(s==NULL)
			{
				ps=ps->next=new StringNode(addrRead,cityCode);
				s=ps;
			}
			p=p->next=new IndexNode(numRead,0,s);
			//保存本次读取的号码以便读下一行时用到
			numLast=numRead;
		}
	}
	p->NumEnd=numLast;
	//关闭源文件
	fclose(fpin);
	/***********************************************/
	int j=0;
	for(p=indexTable;p!=NULL;p=p->next)
	{
		j++;
	}
	int k=0;
	for(ps=stringTable;ps!=NULL;ps=ps->next)
	{
		k++;
	}
	/***********************************************/
	/***********************************************/
	//导入数据文件
	FILE * fpout=fopen(fnout,"wb");
	int header[2]={0,0};  //文件头
	fwrite(&header,sizeof(header),1,fpout);
	int pos=ftell(fpout);
	//写入字符串表
	for(ps=stringTable;ps!=NULL;ps=ps->next)
	{
		pos=ftell(fpout);
		//记下字符串在文件中的偏移量
		ps->offset=pos;
		fwrite(&ps->cityCode,sizeof(unsigned short),1,fpout);
		fwrite(ps->value,1,ps->length+1,fpout);
	}
	//写入索引记录表
	pos=ftell(fpout);
	header[0]=pos;
	IndexStruct is;
	for(p=indexTable;p!=NULL;p=p->next)
	{
		pos=ftell(fpout);
		is.NumStart=p->NumStart;
        fwrite(&is.NumStart,sizeof(int)-1,1,fpout);
		is.NumEnd=p->NumEnd;
        fwrite(&is.NumEnd,sizeof(int)-1,1,fpout);
		is.Offset=p->Address->offset;
        fwrite(&is.Offset,sizeof(unsigned short),1,fpout);
	}
	pos=ftell(fpout);
	header[1]=pos-(sizeof(is)-2);
	//重写文件头
	fseek(fpout,0,SEEK_SET);
	fwrite(&header,sizeof(header),1,fpout);
	//获取数据文件大小
	fseek(fpout,0,SEEK_END);
	pos=ftell(fpout);
	//关闭文件
	fclose(fpout);

    IndexNode *tIdx;
    //释放内存
	for(p=indexTable;p!=NULL;)
	{
        tIdx = p;
        p = p->next;
        delete tIdx;

	}
    
    StringNode *tStr;
	for(ps=stringTable;ps!=NULL;)
	{
        tStr = ps;
        ps = ps->next;
        delete tStr;
	}



	printf("导入成功!\n");
	printf("源文件记录数: %d\n",sfCount);
	printf("目标记录总数: %d\n",j);
	printf("字符串记录数: %d\n",k);
	printf("目标文件大小: %d字节\n",pos);
	/***********************************************/
    return 0;

}


//显示帮助信息
inline void printHelp()
{
    const char * en="Mps";
    printf("手机数据生成程序.\n\n");
    printf("导入数据库: %s -c <数据源文件名>\n",en);
    printf("\n示例:\n");
    printf("   > %s -c MpData.txt   导入MpData.txt到MpData.dat\n",en);
}

//===================主程序入口===================
int main(int argc, char * argv[])
{
    int ret = 0;
	if(argc>1)
	{
		char opcode='h'; 
		char * val = NULL;    //参数值
		//获取操作码和参数值
		for(int i=1;i<argc;i++)
		{
			if(argv[i][0]=='-')
				opcode=argv[i][1];
			else
				val=argv[i];
		}
		//操作选择
		switch(opcode)
		{
		case 'c':
			//导入数据
			ret = MpDataConvert(val,ChangeFileExt(val,"dat"));
			break;
		case 'h':
			//帮助信息
			printHelp();
			ret = 1;
			break;
		default:
			//无操作
			break;
		}
	}

    return ret;
}
