package com.pykj.spring.boot.ykz.chapter3.pojo;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

//@Component
public class BeanPostProcessorExample implements BeanPostProcessor {

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("--------BeanPostProcessor调用postProcessBeforeInitialization方法，参数【"
		        + bean.getClass().getSimpleName()+ "】【" +beanName+"】 ");
		return bean;
	}
	
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("--------BeanPostProcessor调用postProcessAfterInitialization方法，参数【"
	        + bean.getClass().getSimpleName()+ "】【" +beanName+"】 ");
		return bean;
	}

	

}
