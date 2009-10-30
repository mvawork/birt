/*******************************************************************************
 * Copyright (c) 2004, 2005, 2006, 2007, 2008, 2009 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.adapter.oda.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.birt.core.data.ExpressionUtil;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.model.adapter.oda.IODADesignFactory;
import org.eclipse.birt.report.model.adapter.oda.ODADesignFactory;
import org.eclipse.birt.report.model.api.DynamicFilterParameterHandle;
import org.eclipse.birt.report.model.api.FilterConditionHandle;
import org.eclipse.birt.report.model.api.OdaDataSetHandle;
import org.eclipse.birt.report.model.api.ParameterHandle;
import org.eclipse.birt.report.model.api.PropertyHandle;
import org.eclipse.birt.report.model.api.SortHintHandle;
import org.eclipse.birt.report.model.api.StructureFactory;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.eclipse.birt.report.model.api.elements.structures.FilterCondition;
import org.eclipse.birt.report.model.api.elements.structures.SortHint;
import org.eclipse.birt.report.model.api.util.StringUtil;
import org.eclipse.birt.report.model.elements.interfaces.IDataSetModel;
import org.eclipse.birt.report.model.elements.interfaces.IDynamicFilterParameterModel;
import org.eclipse.datatools.connectivity.oda.design.AndExpression;
import org.eclipse.datatools.connectivity.oda.design.CompositeFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.CustomFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.DataSetDesign;
import org.eclipse.datatools.connectivity.oda.design.DynamicFilterExpression;
import org.eclipse.datatools.connectivity.oda.design.ExpressionArguments;
import org.eclipse.datatools.connectivity.oda.design.ExpressionParameterDefinition;
import org.eclipse.datatools.connectivity.oda.design.ExpressionVariable;
import org.eclipse.datatools.connectivity.oda.design.FilterExpression;
import org.eclipse.datatools.connectivity.oda.design.FilterExpressionType;
import org.eclipse.datatools.connectivity.oda.design.NullOrderingType;
import org.eclipse.datatools.connectivity.oda.design.ParameterDefinition;
import org.eclipse.datatools.connectivity.oda.design.ResultSetCriteria;
import org.eclipse.datatools.connectivity.oda.design.ResultSetDefinition;
import org.eclipse.datatools.connectivity.oda.design.SortDirectionType;
import org.eclipse.datatools.connectivity.oda.design.SortKey;
import org.eclipse.datatools.connectivity.oda.design.SortSpecification;
import org.eclipse.emf.common.util.EList;

/**
 * The utility class that converts between ROM filter conditions and ODA filter
 * expression
 * 
 * @see FilterConditionHandle
 * @see FilterExpression
 */
public class ResultSetCriteriaAdapter
{

	/**
	 * The data set handle.
	 */

	private final OdaDataSetHandle setHandle;

	/**
	 * The data set design.
	 */

	private final DataSetDesign setDesign;

	/**
	 * Parameter convert utility
	 */
	private final DynamicFilterParameterAdapter paramAdapter;

	/**
	 * Constant to separate date set name and column name.
	 */
	private final static String SEPERATOR = ":"; //$NON-NLS-1$

	/**
	 * Prefix constant for custom expression.
	 */
	private final static String CUSTOM_PREFIX = "#"; //$NON-NLS-1$

	/**
	 * Prefix constant for dynamic expression.
	 */
	private final static String DYNAMIC_PREFIX = "!"; //$NON-NLS-1$

	/**
	 * 
	 */

	protected final IODADesignFactory designFactory;

	/**
	 * The constructor.
	 * 
	 * @param setHandle
	 *            the data set handle
	 * @param setDesign
	 *            the data set design
	 * 
	 */
	public ResultSetCriteriaAdapter( OdaDataSetHandle setHandle,
			DataSetDesign setDesign )
	{
		this.setHandle = setHandle;
		this.setDesign = setDesign;
		this.paramAdapter = new DynamicFilterParameterAdapter( setHandle,
				setDesign );
		designFactory = ODADesignFactory.getFactory( );
	}

	/**
	 * Updates rom filter and sort hints.
	 * 
	 * @throws SemanticException
	 */
	public void updateROMSortAndFilter( ) throws SemanticException
	{
		ResultSetDefinition resultSet = setDesign.getPrimaryResultSet( );

		if ( resultSet == null )
			return;

		ResultSetCriteria criteria = resultSet.getCriteria( );

		if ( criteria == null )
			return;

		updateROMSortHint( criteria );

		updateROMFilterCondition( criteria );

	}

	/**
	 * Updates oda result set criteria.
	 * 
	 */
	public void updateODAResultSetCriteria( )
	{
		ResultSetDefinition resultSet = setDesign.getPrimaryResultSet( );
		if ( resultSet == null )
			return;

		// if criteria is null, a new criteria will be created.
		ResultSetCriteria criteria = resultSet.getCriteria( );
		if ( criteria == null )
		{
			criteria = designFactory.createResultSetCriteria( );
			resultSet.setCriteria( criteria );
		}

		updateODASortKey( criteria );
		updateOdaFilterExpression( criteria );
	}

	/**
	 * Updates oda filter expression by ROM filter condition.
	 * 
	 * @param criteria
	 *            the result set criteria.
	 */
	private void updateOdaFilterExpression( ResultSetCriteria criteria )
	{

		int count = 0;
		FilterExpression filterExpr = null;
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterHandle = (FilterConditionHandle) iter
					.next( );
			FilterExpression filter = createOdaFilterExpression( filterHandle );
			if ( filter == null )
			{
				continue;
			}
			count++;
			switch ( count )
			{
				case 1 :
					filterExpr = filter;
					break;
				case 2 :
					AndExpression compositeFilterExp = designFactory
							.createAndExpression( );
					compositeFilterExp.add( filterExpr );
					filterExpr = compositeFilterExp;
				default :
					( (CompositeFilterExpression) filterExpr ).add( filter );
			}
		}
		criteria.setFilterSpecification( filterExpr );
	}

	/**
	 * Updates oda sort key.
	 * 
	 */
	private void updateODASortKey( ResultSetCriteria criteria )

	{

		SortSpecification sortSpec = criteria.getRowOrdering( );

		// if an Oda data set has no BIRT sort hints, the Adapter should create
		// an empty SortSpecification.
		if ( sortSpec == null )
		{
			sortSpec = designFactory.createSortSpecification( );
			criteria.setRowOrdering( sortSpec );
		}

		EList<SortKey> list = sortSpec.getSortKeys( );

		// clear the original value.
		list.clear( );

		Iterator<SortHintHandle> iter = setHandle.sortHintsIterator( );

		while ( iter.hasNext( ) )
		{
			SortHintHandle handle = iter.next( );
			SortKey key = designFactory.createSortKey( );
			key.setColumnName( handle.getColumnName( ) );
			key.setColumnPosition( handle.getPosition( ) );

			String ordering = handle.getNullValueOrdering( );

			setODANullValueOrdering( key, ordering );

			key.setOptional( handle.isOptional( ) );

			// default value
			if ( DesignChoiceConstants.SORT_DIRECTION_ASC.equals( handle
					.getDirection( ) ) )
			{
				key.setSortDirection( SortDirectionType.ASCENDING );
			}
			else if ( DesignChoiceConstants.SORT_DIRECTION_DESC.equals( handle
					.getDirection( ) ) )
			{
				key.setSortDirection( SortDirectionType.DESCENDING );
			}

			list.add( key );
		}

	}

	/**
	 * Updates null value ordering value in oda.
	 * 
	 * @param key
	 *            the sort key.
	 * @param ordering
	 *            the ordering.
	 */
	private void setODANullValueOrdering( SortKey key, String ordering )
	{
		if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISFIRST
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.NULLS_FIRST );
		}
		else if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISLAST
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.NULLS_LAST );
		}
		else if ( DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_UNKNOWN
				.equals( ordering ) )
		{
			key.setNullValueOrdering( NullOrderingType.UNKNOWN );
		}
	}

	/**
	 * Updates rom sort hint.
	 * 
	 * @throws SemanticException
	 */
	private void updateROMSortHint( ResultSetCriteria criteria )
			throws SemanticException
	{

		SortSpecification sortSpec = criteria.getRowOrdering( );

		if ( sortSpec == null )
			return;

		PropertyHandle propHandle = setHandle
				.getPropertyHandle( IDataSetModel.SORT_HINTS_PROP );

		// clear the original value.

		propHandle.clearValue( );

		EList<SortKey> list = sortSpec.getSortKeys( );

		for ( int i = 0; i < list.size( ); i++ )
		{
			SortKey key = list.get( i );
			SortHint sortHint = StructureFactory.createSortHint( );

			sortHint.setProperty( SortHint.COLUMN_NAME_MEMBER, key
					.getColumnName( ) );
			sortHint.setProperty( SortHint.POSITION_MEMBER, key
					.getColumnPosition( ) );
			sortHint
					.setProperty( SortHint.IS_OPTIONAL_MEMBER, key.isOptional( ) );

			SortDirectionType sortType = key.getSortDirection( );

			if ( SortDirectionType.ASCENDING.equals( sortType ) )
			{
				sortHint.setProperty( SortHint.DIRECTION_MEMBER,
						DesignChoiceConstants.SORT_DIRECTION_ASC );
			}
			else if ( SortDirectionType.DESCENDING.equals( sortType ) )
			{
				sortHint.setProperty( SortHint.DIRECTION_MEMBER,
						DesignChoiceConstants.SORT_DIRECTION_DESC );
			}

			NullOrderingType type = key.getNullValueOrdering( );

			setROMNullValueOrdering( sortHint, type );

			propHandle.addItem( sortHint );

		}
	}

	/**
	 * Updates null value ordering value in rom.
	 * 
	 * @param hint
	 *            sort hint.
	 * @param type
	 *            the null ordering type.
	 */
	private void setROMNullValueOrdering( SortHint hint, NullOrderingType type )
	{
		if ( NullOrderingType.NULLS_FIRST.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISFIRST );
		}
		else if ( NullOrderingType.NULLS_LAST.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_NULLISLAST );
		}
		else if ( NullOrderingType.UNKNOWN.equals( type ) )
		{
			hint.setProperty( SortHint.NULL_VALUE_ORDERING_MEMBER,
					DesignChoiceConstants.NULL_VALUE_ORDERING_TYPE_UNKNOWN );
		}

	}

	/**
	 * Updates rom filter condition by ODA filter expression.
	 * 
	 * @param criteria
	 *            result set criteria.
	 * @throws SemanticException
	 */
	private void updateROMFilterCondition( ResultSetCriteria criteria )
			throws SemanticException
	{
		FilterExpression filterExpression = null;

		filterExpression = criteria.getFilterSpecification( );

		if ( filterExpression == null )
		{
			return;
		}
		Map<String, Filter> filterExprMap = buildFilterExpressionMap( filterExpression );

		// clears up old filter conditions and finds parameters to refresh
		cleanUpROMFilterCondition( filterExprMap );

		// update exists filter conditions
		updateExistingROMFilterConditions( filterExprMap );
		// sets new filter conditions
		createROMFilterConditions( filterExprMap );

		filterExprMap.clear( );
		filterExprMap = null;
	}

	/**
	 * Builds the filter expression map to convert
	 * 
	 * @param filterExpr
	 *            the filter expression
	 * @return the map containing filter expression to convert
	 */
	private Map<String, Filter> buildFilterExpressionMap(
			FilterExpression filterExpr )
	{
		HashMap<String, Filter> filterExpressions = new LinkedHashMap<String, Filter>( );
		if ( filterExpr != null )
		{
			if ( filterExpr instanceof CompositeFilterExpression )
			{
				CompositeFilterExpression compositeFilterExp = (CompositeFilterExpression) filterExpr;
				if ( compositeFilterExp instanceof AndExpression )
				{
					// Convert and expression only now.
					for ( FilterExpression child : compositeFilterExp
							.getChildren( ) )
					{
						filterExpressions
								.putAll( buildFilterExpressionMap( child ) );
					}
				}
			}
			else if ( filterExpr instanceof CustomFilterExpression )
			{
				Filter customFilter = new CustomFilter(
						(CustomFilterExpression) filterExpr );
				String key = getMapKey( customFilter );
				if ( key != null )
				{
					filterExpressions.put( key, customFilter );
				}
			}
			else if ( filterExpr instanceof DynamicFilterExpression )
			{
				DynamicFilterExpression dynamicFilterExpr = (DynamicFilterExpression) filterExpr;
				boolean isOptional = dynamicFilterExpr.isOptional( );
				ExpressionArguments arguments = dynamicFilterExpr
						.getContextArguments( );
				if ( arguments != null
						&& arguments.getExpressionParameterDefinitions( ) != null )
				{
					for ( ExpressionParameterDefinition paramDefn : arguments
							.getExpressionParameterDefinitions( ) )
					{
						Filter dynamicFilter = new DynamicFilter( paramDefn,
								dynamicFilterExpr.getDefaultType( ), isOptional );
						String key = getMapKey( dynamicFilter );
						if ( key != null )
						{
							filterExpressions.put( key, dynamicFilter );
						}
					}
				}
			}
		}
		return filterExpressions;
	}

	/**
	 * Returns the map key for the given filter.
	 * 
	 * @param filter
	 *            the filter
	 * @return the key for the given filter
	 */
	private String getMapKey( Filter filter )
	{
		String key = null;
		if ( filter instanceof CustomFilter )
		{
			CustomFilter customFilter = (CustomFilter) filter;
			if ( !StringUtil.isBlank( customFilter.getColumnExpr( ) ) )
			{
				key = CUSTOM_PREFIX + customFilter.customFilterExpr.toString( );
			}
		}
		else if ( filter instanceof DynamicFilter )
		{
			String columnExpr = ( (DynamicFilter) filter ).getColumnExpr( );
			if ( !StringUtil.isBlank( columnExpr ) )
			{
				key = DYNAMIC_PREFIX + setHandle.getName( ) + SEPERATOR
						+ columnExpr;
			}
		}
		else
		{
			assert false;
		}
		return key;
	}

	/**
	 * Returns the map key for the given filter condition
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition
	 * @return the key for the given filter handle,or null if the filter
	 *         condition is not dynamic.
	 */
	private String getMapKey( FilterConditionHandle filterConditionHandle )
	{
		String key = null;
		if ( !StringUtil.isBlank( filterConditionHandle
				.getDynamicFilterParameter( ) ) )
		{
			ParameterHandle parameterHandle = setHandle.getModuleHandle( )
					.findParameter(
							filterConditionHandle.getDynamicFilterParameter( ) );
			if ( parameterHandle instanceof DynamicFilterParameterHandle )
			{
				DynamicFilterParameterHandle dynamicFilterParamHandle = (DynamicFilterParameterHandle) parameterHandle;
				key = DYNAMIC_PREFIX
						+ dynamicFilterParamHandle.getDataSetName( )
						+ SEPERATOR + dynamicFilterParamHandle.getColumn( );
			}
		}
		return key;
	}

	/**
	 * Updates existing filter conditions with the given filters
	 * 
	 * @param filterMap
	 *            the map containing the filters to update
	 * @throws SemanticException
	 */
	private void updateExistingROMFilterConditions(
			Map<String, Filter> filterMap ) throws SemanticException
	{
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterConditionHandle = (FilterConditionHandle) iter
					.next( );
			String key = getMapKey( filterConditionHandle );
			if ( key != null && filterMap.containsKey( key ) )
			{
				DynamicFilterParameterHandle dynamicFilterParamHandle = (DynamicFilterParameterHandle) setHandle
						.getModuleHandle( ).findParameter(
								filterConditionHandle
										.getDynamicFilterParameter( ) );
				updateDynamicFilterCondition( filterConditionHandle,
						(DynamicFilter) filterMap.get( key ),
						dynamicFilterParamHandle );
				// Removes the filter from the map after updated
				filterMap.remove( key );
			}
			else
			{// not expected
				assert false;
			}
		}
	}

	/**
	 * Creates new filter conditions by the given filters
	 * 
	 * @param filterMap
	 *            the map containing filters
	 * @throws SemanticException
	 */
	private void createROMFilterConditions( Map<String, Filter> filterMap )
			throws SemanticException
	{
		for ( Filter filter : filterMap.values( ) )
		{
			FilterCondition filterCondition = StructureFactory
					.createFilterCond( );
			filterCondition.setExpr( filter.getColumnExpr( ) );
			FilterConditionHandle filterConditionHandle = (FilterConditionHandle) setHandle
					.getPropertyHandle( IDataSetModel.FILTER_PROP ).addItem(
							filterCondition );
			if ( filter instanceof CustomFilter )
			{
				CustomFilterExpression customFilterExp = ( (CustomFilter) filter ).customFilterExpr;
				updateCustomFilterCondition( filterConditionHandle,
						customFilterExp );
			}
			else if ( filter instanceof DynamicFilter )
			{
				DynamicFilter dynamicFilter = (DynamicFilter) filter;
				// creates new dynamic filter parameter
				DynamicFilterParameterHandle dynamicFilterParamHandle = setHandle
						.getModuleHandle( ).getElementFactory( )
						.newDynamicFilterParameter( null );
				dynamicFilterParamHandle.setDataSetName( setHandle.getName( ) );
				dynamicFilterParamHandle.setColumn( dynamicFilter
						.getColumnName( ) );
				Integer nativeDataType = dynamicFilter.getNativeDataType( );
				if ( nativeDataType != null )
				{
					dynamicFilterParamHandle.setNativeDataType( nativeDataType );
					try
					{
						dynamicFilterParamHandle
								.setDataType( NativeDataTypeUtil
										.getUpdatedDataType(
												setDesign
														.getOdaExtensionDataSourceId( ),
												setDesign
														.getOdaExtensionDataSetId( ),
												nativeDataType,
												null,
												DesignChoiceConstants.CHOICE_PARAM_TYPE ) );
					}
					catch ( BirtException e )
					{
						// Do nothing
					}
				}
				setHandle.getModuleHandle( ).getParameters( ).add(
						dynamicFilterParamHandle );
				// sets the reference
				filterConditionHandle
						.setDynamicFilterParameter( dynamicFilterParamHandle
								.getName( ) );
				// updates the dynamic filter parameter
				updateDynamicFilterCondition( filterConditionHandle,
						dynamicFilter, dynamicFilterParamHandle );
			}
		}
	}

	/**
	 * Updates the filter condition by the given custom filter expression
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition to update
	 * @param customFilterExpr
	 *            the custom filter expression
	 * @throws SemanticException
	 */
	private void updateCustomFilterCondition(
			FilterConditionHandle filterConditionHandle,
			CustomFilterExpression customFilterExpr ) throws SemanticException
	{
		FilterExpressionType tmpType = customFilterExpr.getType( );
		if ( tmpType != null )
		{
			filterConditionHandle.setExtensionName( tmpType
					.getDeclaringExtensionId( ) );
			filterConditionHandle.setExtensionExprId( tmpType.getId( ) );
		}

		filterConditionHandle.setPushDown( true );
		filterConditionHandle.setOptional( customFilterExpr.isOptional( ) );
	}

	/**
	 * Updates the filter condition by the given dynamic filter
	 * 
	 * @param filterConditionHandle
	 *            the handle of the filter condition to update
	 * @param dynamicFilterExpr
	 *            the dynamic filter
	 * @throws SemanticException
	 */
	private void updateDynamicFilterCondition(
			FilterConditionHandle filterConditionHandle,
			DynamicFilter dynamicFilter,
			DynamicFilterParameterHandle dynamicFilterParamHandle )
			throws SemanticException
	{
		filterConditionHandle.setOptional( dynamicFilter.isOptional );

		paramAdapter.updateROMDynamicFilterParameter( dynamicFilter,
				dynamicFilterParamHandle );
	}

	/**
	 * Clears up all unnecessary dynamic filter parameter and filter conditions
	 * 
	 * @param filterMap
	 *            the map contains filters
	 * @throws SemanticException
	 * 
	 */
	private void cleanUpROMFilterCondition( Map<String, Filter> filterMap )
			throws SemanticException
	{
		ArrayList<FilterCondition> dropList = new ArrayList<FilterCondition>( );
		for ( Iterator iter = setHandle.filtersIterator( ); iter.hasNext( ); )
		{
			FilterConditionHandle filterHandle = (FilterConditionHandle) iter
					.next( );
			String dynamicParameterName = filterHandle
					.getDynamicFilterParameter( );
			String key = getMapKey( filterHandle );
			// Check if contains such filter.
			if ( key != null && !filterMap.containsKey( key ) )
			{
				// Remove the filter condition which is not contained.
				if ( !StringUtil.isBlank( dynamicParameterName ) )
				{
					ParameterHandle parameterHandle = setHandle
							.getModuleHandle( ).findParameter(
									dynamicParameterName );
					parameterHandle.drop( );
				}
				dropList.add( (FilterCondition) filterHandle.getStructure( ) );
			}
		}
		for ( FilterCondition fc : dropList )
		{
			setHandle.removeFilter( fc );
		}

	}

	/**
	 * Creates the oda filter expression by the given filter condition.
	 * 
	 * @param filterHandle
	 *            the handle of the given filter condition
	 * @return the filter expression created
	 */
	private FilterExpression createOdaFilterExpression(
			FilterConditionHandle filterHandle )
	{
		FilterExpression filterExpr = null;
		if ( !StringUtil.isBlank( filterHandle.getExtensionName( ) ) )
		{
			CustomFilterExpression customFilterExpr = designFactory
					.createCustomFilterExpression( );

			ExpressionVariable variable = designFactory
					.createExpressionVariable( );
			variable.setIdentifier( filterHandle.getExpr( ) );
			customFilterExpr.setContextVariable( variable );

			// update the expression type. The default value.

			if ( filterHandle.getExtensionName( ) != null
					|| filterHandle.getExtensionExprId( ) != null )
			{
				FilterExpressionType tmpType = designFactory
						.createFilterExpressionType( );
				tmpType.setDeclaringExtensionId( filterHandle
						.getExtensionName( ) );
				tmpType.setId( filterHandle.getExtensionExprId( ) );

				customFilterExpr.setType( tmpType );
			}

			customFilterExpr.setIsOptional( filterHandle.isOptional( ) );

			filterExpr = customFilterExpr;
		}
		else if ( !StringUtil
				.isBlank( filterHandle.getDynamicFilterParameter( ) ) )
		{
			ParameterHandle paramHandle = setHandle.getModuleHandle( )
					.findParameter( filterHandle.getDynamicFilterParameter( ) );
			if ( paramHandle instanceof DynamicFilterParameterHandle )
			{
				DynamicFilterParameterHandle dynamicParamHandle = (DynamicFilterParameterHandle) paramHandle;
				DynamicFilterExpression dynamicFilterExpr = designFactory
						.createDynamicFilterExpression( );
				dynamicFilterExpr.setIsOptional( filterHandle.isOptional( ) );

				ExpressionArguments arguments = designFactory
						.createExpressionArguments( );
				ParameterDefinition paramDefn = designFactory
						.createParameterDefinition( );

				paramAdapter.updateODADynamicFilter( paramDefn,
						dynamicParamHandle );

				paramDefn.getAttributes( ).setName(
						dynamicParamHandle.getColumn( ) );
				if ( dynamicParamHandle
						.getProperty( IDynamicFilterParameterModel.NATIVE_DATA_TYPE_PROP ) != null )
				{
					paramDefn.getAttributes( ).setNativeDataTypeCode(
							dynamicParamHandle.getNativeDataType( ) );
				}

				arguments.addDynamicParameter( paramDefn );
				dynamicFilterExpr.setContextArguments( arguments );

				filterExpr = dynamicFilterExpr;
			}
		}
		return filterExpr;
	}

	private static interface Filter
	{

		/**
		 * Returns the column expression for the dynamic filter.
		 * 
		 * @return the column expression for the dynamic filter.
		 */
		String getColumnExpr( );
	}

	private static class CustomFilter implements Filter
	{

		public CustomFilterExpression customFilterExpr;

		public CustomFilter( CustomFilterExpression filterExpr )
		{
			this.customFilterExpr = filterExpr;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.adapter.oda.impl.ResultSetCriteriaAdapter
		 * .Filter#getColumnExpr()
		 */
		public String getColumnExpr( )
		{
			ExpressionVariable variable = customFilterExpr.getContextVariable( );
			if ( variable != null )
			{
				return variable.getIdentifier( );
			}
			return null;
		}
	}

	static class DynamicFilter implements Filter
	{

		/**
		 * Identifies if the filter is optional.
		 */
		public boolean isOptional;

		public ExpressionParameterDefinition exprParamDefn;

		public FilterExpressionType defaultType;

		public DynamicFilter( ExpressionParameterDefinition exprParamDefn,
				FilterExpressionType defaultType, boolean isOptional )
		{
			this.isOptional = isOptional;
			this.exprParamDefn = exprParamDefn;
			this.defaultType = defaultType;
		}

		/**
		 * Returns the column name for the dynamic filter.
		 * 
		 * @return the column name for the dynamic filter.
		 */
		public String getColumnName( )
		{
			ParameterDefinition paramDefn = exprParamDefn
					.getDynamicInputParameter( );
			if ( paramDefn != null && paramDefn.getAttributes( ) != null )
			{
				return paramDefn.getAttributes( ).getName( );
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.adapter.oda.impl.ResultSetCriteriaAdapter
		 * .Filter#getColumnExpr()
		 */
		public String getColumnExpr( )
		{
			String columnName = getColumnName( );
			if ( !StringUtil.isBlank( columnName ) )
			{
				return ExpressionUtil.createDataSetRowExpression( columnName );
			}
			return null;
		}

		public Integer getNativeDataType( )
		{
			ParameterDefinition paramDefn = exprParamDefn
					.getDynamicInputParameter( );
			if ( paramDefn != null && paramDefn.getAttributes( ) != null )
			{
				return paramDefn.getAttributes( ).getNativeDataTypeCode( );
			}
			return null;
		}

		/**
		 * @return the defaultType
		 */

		public FilterExpressionType getDefaultType( )
		{
			return defaultType;
		}
	}
}
