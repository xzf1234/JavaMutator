package edu.hust.xzf.mutator.fixtemplate;

import java.util.List;

import edu.hust.xzf.jdt.tree.ITree;
import edu.hust.xzf.mutator.context.Dictionary;
import edu.hust.xzf.mutator.info.Patch;

/**
 * FixTemplate interface.
 * 
 * @author kui.liu
 *
 */
public interface IFixTemplate {
	
	public void setSuspiciousCodeStr(String suspiciousCodeStr);
	
	public String getSuspiciousCodeStr();
	
	public void setSuspiciousCodeTree(ITree suspiciousCodeTree);
	
	public ITree getSuspiciousCodeTree();
	
	public void generatePatches();
	
	public List<Patch> getPatches();
	
	public String getSubSuspiciouCodeStr(int startPos, int endPos);
	
	public void setDictionary(Dictionary dic);
}
