package com.thomasdiewald.pixelflow.java.imageprocessing.filter;

import com.jogamp.opengl.GL2;
import com.thomasdiewald.pixelflow.java.DwPixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLTexture;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.DwFilter;
import com.thomasdiewald.pixelflow.java.imageprocessing.filter.Sobel;

import processing.opengl.PGraphicsOpenGL;

public class DwParticleFluidFX {

  
  static public class Param{
    public int     level_of_detail               = 1;
    public int     blur_radius                   = 1;
    public boolean apply_hightlights             = true;
    public float   highlight                     = 0.60f;
    public boolean apply_subsurfacescattering    = true;
    public float   subsurfacescattering          = 0.5f;
  }
  
  // parameter
  public Param param = new Param();
  
  // pixelflow context
  public DwPixelFlow context;
  
  // copy of the original texture, way faster to work with
  public DwGLTexture tex_particles = new DwGLTexture();
  
  public DwParticleFluidFX(DwPixelFlow context){
    this.context = context;
  }
  

  protected final int SWIZZLE_R = GL2.GL_RED;
  protected final int SWIZZLE_G = GL2.GL_GREEN;
  protected final int SWIZZLE_B = GL2.GL_BLUE;
  protected final int SWIZZLE_A = GL2.GL_ALPHA;
  protected final int SWIZZLE_0 = GL2.GL_ZERO;
  protected final int SWIZZLE_1 = GL2.GL_ONE;

  protected int[] swizzle_RGBA = {SWIZZLE_R, SWIZZLE_G, SWIZZLE_B, SWIZZLE_A};
  protected int[] swizzle_RGB0 = {SWIZZLE_R, SWIZZLE_G, SWIZZLE_B, SWIZZLE_0};
  protected int[] swizzle_RGB1 = {SWIZZLE_R, SWIZZLE_G, SWIZZLE_B, SWIZZLE_1};
  protected int[] swizzle_AAA0 = {SWIZZLE_A, SWIZZLE_A, SWIZZLE_A, SWIZZLE_0};
  protected int[] swizzle_AAA1 = {SWIZZLE_A, SWIZZLE_A, SWIZZLE_A, SWIZZLE_1};
  protected int[] swizzle_AAAA = {SWIZZLE_A, SWIZZLE_A, SWIZZLE_A, SWIZZLE_A};
  

  protected float[] mad_A = new float[]{1,0};
  protected float[] mad_B = new float[]{1,0};
  
  
  public void apply(PGraphicsOpenGL pg_particles){
    apply(pg_particles, pg_particles);
  }
  
  
  public void apply(PGraphicsOpenGL pg_src, PGraphicsOpenGL pg_dst){
    int w = pg_src.width;
    int h = pg_src.height;
    tex_particles.resize(context, GL2.GL_RGBA8, w, h, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, GL2.GL_LINEAR, 4, 1);
    
    DwFilter.get(context).copy.apply(pg_src, tex_particles);
    
    apply(tex_particles);
    
    DwFilter.get(context).copy.apply(tex_particles, pg_dst);
  }
  
  
  public void apply(DwGLTexture tex_particles){
    apply(tex_particles, tex_particles);
  }
  
  
  public void apply(DwGLTexture tex_src, DwGLTexture tex_dst){
    
    int LOD = param.level_of_detail;
    
    final DwFilter filter = DwFilter.get(context);
    
    filter.gausspyramid.setBlurLayers(LOD + 3);
    filter.gausspyramid.apply(tex_src, param.blur_radius);
   
    // make sure to not get any OOB
    LOD = Math.min(LOD, filter.gausspyramid.getNumBlurLayers() - 3);
    
    DwGLTexture tex_blur_base = filter.gausspyramid.tex_blur[LOD];
  
    // surface highlights
    if(param.apply_hightlights)
    {
      DwGLTexture tex_blur = filter.gausspyramid.tex_blur[LOD];
      DwGLTexture tex_edge = filter.gausspyramid.tex_temp[LOD];
      
      float th = param.highlight;
      
//      // based on luminance edge
//      filter.sobel.apply(tex_blur, tex_edge, Sobel.TYPE._3x3_VERT, new float[]{-0.5f,0});
//      filter.luminance.apply(tex_edge, tex_edge);
//      filter.multiply.apply(tex_edge, tex_edge, new float[]{2,2,2,0});
//      filter.threshold.apply(tex_edge, tex_edge, new float[]{0.5f, 0.5f, 0.5f, 0.0f});
//      filter.merge.apply(tex_blur_base, tex_blur_base, tex_edge, mad_A, mad_B);
      
      //  based on alpha edge
      filter.sobel.apply(tex_blur, tex_edge, Sobel.TYPE._3x3_VERT, new float[]{-0.5f,0});
      tex_edge.swizzle(swizzle_AAA0);
      filter.threshold.apply(tex_edge, tex_edge, new float[]{th, th, th, 0.0f});
      tex_edge.swizzle(swizzle_RGB0);
      filter.merge.apply(tex_blur_base, tex_blur_base, tex_edge, mad_A, mad_B);
      tex_edge.swizzle(swizzle_RGBA);
    }

    filter.copy.apply(tex_blur_base, tex_dst);
    
    // subsurface scattering
    if(param.apply_subsurfacescattering)
    {
      DwGLTexture tex_blur = filter.gausspyramid.tex_blur[LOD + 2];
      DwGLTexture tex_edge = filter.gausspyramid.tex_temp[LOD + 1];
      
      float add = param.subsurfacescattering;
      float mul = 1.5f/(0.5f + add);
      float[] mad = new float[]{mul, add};
      
      filter.sobel.apply(tex_blur, tex_edge, Sobel.TYPE._3x3_VERT, new float[]{-0.5f,0.0f});
      tex_edge.swizzle(swizzle_AAA1);
      filter.mad.apply(tex_edge, tex_edge, mad);
      tex_edge.swizzle(swizzle_RGB1);
      filter.multiply.apply(tex_dst, tex_dst, tex_edge);
      tex_blur.swizzle(swizzle_RGBA);
      tex_edge.swizzle(swizzle_RGBA);
    }
    

    // cut border, AA thresholding
    filter.threshold.apply(tex_dst, tex_dst, new float[]{0.0f, 0.0f, 0.0f, 0.7f});

  }
  
}
