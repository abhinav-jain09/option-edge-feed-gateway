(() => {
  const MarketCalendar = createMarketCalendar();
  window.OptionChainMarketCalendar = MarketCalendar;

  const rootElement = document.getElementById('optionChainRoot');
  if (!window.React || !window.ReactDOM) {
    rootElement.innerHTML = '<div class="react-error">React assets failed to load from the WAR. Refresh the page after the latest deploy.</div>';
    return;
  }

  const { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } = React;
  const h = React.createElement;
  const ROW_RENDER_THROTTLE_MS = 125;
  // GEX history popover time-slice windows are SOURCE-DEPENDENT. Unusual Whales publishes a full
  // trading-day history (10m/30m/1h/4h/8h); the Databento 0DTE history service publishes tighter
  // intraday slices (5m/15m/30m/1h/2h). Selecting per-source keeps each popover rendering exactly
  // the windows its producer emits — changing one must never break the other.
  const GEX_HISTORY_WINDOWS_BY_SOURCE = {
    databento: ['5m', '15m', '30m', '1h', '2h'],
    default: ['10m', '30m', '1h', '4h', '8h']
  };
  // Legacy alias (Unusual Whales windows) kept for any external reference / default callers.
  const GEX_HISTORY_WINDOWS = GEX_HISTORY_WINDOWS_BY_SOURCE.default;
  function gexHistoryWindowsForSource(source) {
    return String(source || '').toUpperCase() === 'DATABENTO'
      ? GEX_HISTORY_WINDOWS_BY_SOURCE.databento
      : GEX_HISTORY_WINDOWS_BY_SOURCE.default;
  }
  const runtimeEnv = window.__OPTIONS_EDGE_ENV__ || {};

  const defaultConfig = {
    appProfile: runtimeEnv.VITE_APP_PROFILE || runtimeEnv.APP_PROFILE || 'dev',
    apiBaseUrl: runtimeEnv.VITE_API_BASE_URL || '',
    missionControlUrl: runtimeEnv.VITE_MISSION_CONTROL_URL || '',
    provider: 'IB',
    marketDataSource: 'DATABENTO',
    symbol: '',
    expiry: '',
    port: 4001,
    clientId: 0,
    maxStrikes: 43,
    delayed: false,
    feedGatewayEnabled: true,
    feedGatewayAvailable: true,
    feedGatewayWsUrl: runtimeEnv.VITE_WS_URL || '',
    databentoReplayUiEnabled: Boolean(runtimeEnv.DATABENTO_REPLAY_UI_ENABLED)
		                  };
	                  const wingWidth = 5;
	                  const emptySandwichState = { callTop2: [], putTop2: [], currentTop2: [], trackedSandwiches: [], alerts: [] };
                  const emptyHpsfState = { latestSignal: undefined, marketFlow: undefined, topCandidates: undefined, audit: undefined, exitIntent: undefined, validation: undefined };

	                  function OptionChainApp() {
	    const rowsRef = useRef(new Map());
	    const pacesRef = useRef(new Map());
	    const gexByStrikeRef = useRef(new Map());
	    const strikeFlowRef = useRef(new Map());
    const visibleStrikesRef = useRef([]);
	    const tableWrapRef = useRef(null);
    const tableBodyRef = useRef(null);
    const centerTimersRef = useRef([]);
    const didInitialCenterRef = useRef(false);
    const wsRef = useRef(null);
    const reconnectTimerRef = useRef(undefined);
    const rowRenderTimerRef = useRef(undefined);
    const rowRenderPendingRef = useRef(false);
    const hpsfLastSignalKeyRef = useRef('');
    const hpsfPulseTimerRef = useRef(undefined);

    const [config, setConfig] = useState(defaultConfig);
    const [form, setForm] = useState(defaultConfig);
    const [configReady, setConfigReady] = useState(false);
    const [status, setStatus] = useState({ text: 'Starting', mode: '' });
	                    const [notice, setNotice] = useState('');
	                    const [connectPending, setConnectPending] = useState(false);
	                    const [latestSnapshot, setLatestSnapshot] = useState(undefined);
	                    const [rowsVersion, setRowsVersion] = useState(0);
		                    const [selectedSpread, setSelectedSpread] = useState(undefined);
			                    const [orderQuantity, setOrderQuantity] = useState(1);
				                    const [orderPending, setOrderPending] = useState(false);
					                    const [volumeSandwichState, setVolumeSandwichState] = useState(emptySandwichState);
					                    const [directionalPressure, setDirectionalPressure] = useState(undefined);
					                    const [vixPrice, setVixPrice] = useState(undefined);
					                    const [esPrice, setEsPrice] = useState(undefined);
	                            const [hpsfState, setHpsfState] = useState(emptyHpsfState);
                            const [hpsfPulse, setHpsfPulse] = useState(false);
			                    const [paceWindow, setPaceWindow] = useState('1m');
			                    const [pressureWindow, setPressureWindow] = useState('1m');
			                    const [amsterdamTime, setAmsterdamTime] = useState(amsterdamTimeLabel);

    const cancelScheduledRowRender = useCallback(() => {
      if (rowRenderTimerRef.current !== undefined) {
        window.clearTimeout(rowRenderTimerRef.current);
        rowRenderTimerRef.current = undefined;
      }
      rowRenderPendingRef.current = false;
    }, []);
    const bumpRows = useCallback(() => {
      if (rowRenderPendingRef.current) return;
      rowRenderPendingRef.current = true;
      rowRenderTimerRef.current = window.setTimeout(() => {
        rowRenderTimerRef.current = undefined;
        rowRenderPendingRef.current = false;
        setRowsVersion(version => version + 1);
      }, ROW_RENDER_THROTTLE_MS);
    }, []);
    const applyConfigState = useCallback(nextConfig => {
      const normalized = normalizeConfig(nextConfig);
      setConfig(normalized);
      setForm(normalized);
      setConfigReady(true);
    }, []);
    const clearData = useCallback(() => {
      cancelScheduledRowRender();
      centerTimersRef.current.forEach(window.clearTimeout);
	      centerTimersRef.current = [];
	      didInitialCenterRef.current = false;
      visibleStrikesRef.current = [];
	      rowsRef.current.clear();
	                      pacesRef.current.clear();
                      gexByStrikeRef.current.clear();
                      strikeFlowRef.current.clear();
		                      setLatestSnapshot(undefined);
		                      setSelectedSpread(undefined);
			                      setVolumeSandwichState(emptySandwichState);
			                      setDirectionalPressure(undefined);
			                      setVixPrice(undefined);
			                      setEsPrice(undefined);
	                              setHpsfState(emptyHpsfState);
                              hpsfLastSignalKeyRef.current = '';
                              if (hpsfPulseTimerRef.current !== undefined) {
                                window.clearTimeout(hpsfPulseTimerRef.current);
                                hpsfPulseTimerRef.current = undefined;
                              }
                              setHpsfPulse(false);
				                      setRowsVersion(version => version + 1);
			                    }, [cancelScheduledRowRender]);

    const mergeSnapshotPayload = useCallback(payload => {
      const key = contractKey(payload);
      const pace = pacesRef.current.get(key);
      const gex = gexByStrikeRef.current.get(key);
      const strikeFlow = strikeFlowRef.current.get(key);
      rowsRef.current.set(key, { ...payload, ...(pace || {}), ...(gex || {}), ...(strikeFlow || {}) });
      return true;
    }, []);

    const mergePacePayload = useCallback(payload => {
      const key = contractKey(payload);
      pacesRef.current.set(key, payload);
      const row = rowsRef.current.get(key);
      if (!row) return false;
      rowsRef.current.set(key, { ...row, ...payload });
      return true;
    }, []);

    const mergeGexPayload = useCallback(payload => {
      const gex = normalizeGexPayload(payload);
      if (!Number.isFinite(gex.strike)) return false;
      const key = contractKey(gex);
      const previous = gexByStrikeRef.current.get(key);
      const previousNetGex = Number(previous?.uwNetGex);
      const currentNetGex = Number(gex.uwNetGex);
      if (Number.isFinite(previousNetGex) && Number.isFinite(currentNetGex)) {
        gex.uwPreviousNetGex = previousNetGex;
        gex.uwGexChange = currentNetGex - previousNetGex;
        gex.uwGexMoveDirection = gexHorizontalDirection(gex.uwGexChange);
      }
      gexByStrikeRef.current.set(key, gex);
      const row = rowsRef.current.get(key);
      if (!row) return false;
      rowsRef.current.set(key, { ...row, ...gex });
      return true;
    }, []);

    const mergeStrikeFlowPayload = useCallback(payload => {
      const strikes = Array.isArray(payload?.strikes) ? payload.strikes : [];
      if (!strikes.length) return false;
      let changed = false;
      strikes.forEach(strikePayload => {
        const strike = Number(strikePayload?.strike);
        if (!Number.isFinite(strike)) return;
        const key = contractKey({ ...payload, strike });
        const prevFlow = strikeFlowRef.current.get(key)?.strikeFlow;
        const strikeFlow = normalizeStrikeFlowPayload(payload, strikePayload, prevFlow);
        strikeFlowRef.current.set(key, strikeFlow);
        const row = rowsRef.current.get(key);
        if (!row) return;
        rowsRef.current.set(key, { ...row, ...strikeFlow });
        changed = true;
      });
      return changed;
    }, []);

    const applyVolumeSandwichPayload = useCallback(payload => {
      const currentTop2 = payload.currentTop2 || [...(payload.callTop2 || []), ...(payload.putTop2 || [])];
      setVolumeSandwichState({
        callTop2: payload.callTop2 || [],
        putTop2: payload.putTop2 || [],
        currentTop2,
        trackedSandwiches: payload.trackedSandwiches || [],
        alerts: payload.alerts || []
      });
    }, []);

    const applyVolumeSandwichAlertPayload = useCallback(alert => {
      setVolumeSandwichState(current => ({
        callTop2: current.callTop2 || [],
        putTop2: current.putTop2 || [],
        currentTop2: current.currentTop2 || [],
        trackedSandwiches: current.trackedSandwiches || [],
        alerts: [alert, ...(current.alerts || [])].slice(0, 3)
      }));
    }, []);

    const triggerHpsfPulse = useCallback(signal => {
      const signalKey = hpsfSignalKey(signal);
      if (!signalKey || hpsfLastSignalKeyRef.current === signalKey) return;
      hpsfLastSignalKeyRef.current = signalKey;
      if (hpsfPulseTimerRef.current !== undefined) {
        window.clearTimeout(hpsfPulseTimerRef.current);
      }
      setHpsfPulse(true);
      hpsfPulseTimerRef.current = window.setTimeout(() => {
        hpsfPulseTimerRef.current = undefined;
        setHpsfPulse(false);
      }, 1000);
    }, []);

    const applyHpsfViews = useCallback(update => {
      const latestSignal = update.latestSignal;
      if (latestSignal) triggerHpsfPulse(latestSignal);
      setHpsfState(current => ({
        latestSignal: latestSignal || current.latestSignal,
        marketFlow: update.marketFlow || current.marketFlow,
        topCandidates: update.topCandidates || current.topCandidates,
        audit: update.audit || current.audit,
        exitIntent: update.exitIntent || current.exitIntent,
        validation: update.validation || current.validation
      }));
    }, [triggerHpsfPulse]);

    const applyUiBatch = useCallback((payload, activeConfig) => {
      let rowsChanged = false;
      let latestAcceptedSnapshot;
      batchItems(payload, 'snapshots').forEach(snapshot => {
        if (!shouldAcceptStreamPayload(snapshot, activeConfig)) return;
        rowsChanged = mergeSnapshotPayload(snapshot) || rowsChanged;
        latestAcceptedSnapshot = snapshot;
      });
      batchItems(payload, 'paces').forEach(pace => {
        if (!shouldAcceptStreamPayload(pace, activeConfig)) return;
        rowsChanged = mergePacePayload(pace) || rowsChanged;
      });
      batchItems(payload, 'gexByStrike').forEach(gex => {
        if (!shouldAcceptStreamPayload(gex, activeConfig)) return;
        rowsChanged = mergeGexPayload(gex) || rowsChanged;
      });
      batchItems(payload, 'strikeFlows').forEach(strikeFlow => {
        if (!shouldAcceptStrikeFlowPayload(strikeFlow, activeConfig)) return;
        rowsChanged = mergeStrikeFlowPayload(strikeFlow) || rowsChanged;
      });
      if (latestAcceptedSnapshot) setLatestSnapshot(latestAcceptedSnapshot);
	      const latestPressure = lastAcceptedPayload(batchItems(payload, 'directionalPressures'), activeConfig);
	      if (latestPressure) setDirectionalPressure(latestPressure);
	      const latestVixPrice = lastIndexPricePayload(batchItems(payload, 'indexPrices'), activeConfig, 'VIX');
	      if (latestVixPrice) setVixPrice(latestVixPrice);
	      const latestEsPrice = lastIndexPricePayload(batchItems(payload, 'indexPrices'), activeConfig, ['ESM6', 'ES.V.0']);
	      if (latestEsPrice) setEsPrice(latestEsPrice);
	      const latestSandwich = lastAcceptedPayload(batchItems(payload, 'volumeSandwiches'), activeConfig);
      if (latestSandwich) applyVolumeSandwichPayload(latestSandwich);
      batchItems(payload, 'volumeSandwichAlerts').forEach(item => {
        const alert = item.alert || item;
        if (shouldAcceptStreamPayload(alert, activeConfig)) applyVolumeSandwichAlertPayload(alert);
      });
      applyHpsfViews({
        latestSignal: lastPayload(batchItems(payload, 'hpsfLatestSignals')),
        marketFlow: lastPayload(batchItems(payload, 'hpsfMarketFlows')),
        topCandidates: lastPayload(batchItems(payload, 'hpsfTopCandidates')),
        audit: lastPayload(batchItems(payload, 'hpsfAudits')),
        exitIntent: lastPayload(batchItems(payload, 'hpsfExitIntents')),
        validation: lastPayload(batchItems(payload, 'hpsfValidations'))
      });
      if (rowsChanged) bumpRows();
    }, [applyHpsfViews, applyVolumeSandwichAlertPayload, applyVolumeSandwichPayload, bumpRows, mergeGexPayload, mergePacePayload, mergeSnapshotPayload, mergeStrikeFlowPayload]);

    useEffect(() => {
      fetch(apiUrl('/api/config'), { cache: 'no-store' })
        .then(response => response.json())
        .then(applyConfigState)
        .catch(error => {
          setStatus({ text: 'Failed', mode: 'error' });
          setNotice(error.message);
        });
    }, [applyConfigState]);

    // NOTE: no after-close expiry auto-rollover. The configured expiry (from /api/config, resolved by
    // the deploy to the same chain date the Databento feed publishes) is authoritative. Advancing it
    // on a local clock rule pointed the chain at a date the feed never publishes, blanking the UI
    // after the 16:15 close. The expiry now advances only when the deploy re-resolves it.

    useEffect(() => {
      if (!configReady) return undefined;
      let stopped = false;

      if (!config.feedGatewayEnabled) {
        setStatus({ text: 'Feed unavailable', mode: 'error' });
        setNotice('Feed gateway is disabled in OptionsEdge config.');
        clearData();
        return () => {
          stopped = true;
        };
      }

      if (config.feedGatewayAvailable === false) {
        setStatus({ text: 'Feed unavailable', mode: 'error' });
        setNotice(feedUnavailableNotice(config));
        clearData();
        const configRefreshTimer = window.setInterval(() => {
          fetch(apiUrl('/api/config'), { cache: 'no-store' })
            .then(response => response.json())
            .then(applyConfigState)
            .catch(() => {});
        }, 2000);
        return () => {
          stopped = true;
          window.clearInterval(configRefreshTimer);
        };
      }

      const openSocket = () => {
        if (stopped) return;
        const socket = new WebSocket(gatewayWebSocketUrl(config));
        wsRef.current = socket;

        socket.onopen = () => {
          setStatus({ text: 'Streaming', mode: 'live' });
          setNotice('');
        };

        socket.onmessage = event => {
          let message;
          try {
            message = JSON.parse(event.data);
          } catch (error) {
            setStatus({ text: 'Bad Data', mode: 'error' });
            setNotice(error.message);
            return;
          }

          const payload = message.data || {};
          if (message.type === 'status') {
            setStatus({ text: 'Streaming', mode: 'live' });
          } else if (message.type === 'source-switching') {
            applyConfigState(selectionConfig(config, payload));
            clearData();
            setStatus({ text: 'Switching source...', mode: '' });
            setNotice(`Switching source to ${payload.marketDataSource || config.marketDataSource} for ${payload.symbol || config.symbol} ${formatExpiry(payload.expiry || config.expiry)}...`);
          } else if (message.type === 'source-ready') {
            applyConfigState(selectionConfig(config, payload));
            setStatus({ text: 'Streaming', mode: 'live' });
            setNotice('');
          } else if (message.type === 'source-stale') {
            applyConfigState(selectionConfig(config, payload));
            setStatus({ text: 'Waiting for fresh source data', mode: 'stale' });
            setNotice(`Waiting for fresh ${payload.marketDataSource || config.marketDataSource} data for ${payload.symbol || config.symbol} ${formatExpiry(payload.expiry || config.expiry)}.`);
          } else if (message.type === 'connected') {
            setStatus({ text: 'Connected', mode: 'live' });
            setNotice('');
            if (payload.config) applyConfigState(payload.config);
	          } else if (message.type === 'reset') {
	            clearData();
	            if (payload.config) applyConfigState(payload.config);
		          } else if (message.type === 'ui-batch') {
		            applyUiBatch(payload, config);
		          } else if (message.type === 'snapshot') {
		            if (!shouldAcceptStreamPayload(payload, config)) return;
		            mergeSnapshotPayload(payload);
		            setLatestSnapshot(payload);
		            bumpRows();
		          } else if (message.type === 'remove-strike') {
		            const strike = Number(payload.strike);
		            const removeAllSources = String(payload.source || payload.marketDataSource || '').toUpperCase() === 'ALL';
		            const keys = removeAllSources
		              ? matchingStrikeKeys([rowsRef.current, pacesRef.current, gexByStrikeRef.current, strikeFlowRef.current], payload)
		              : [activeContractKey(payload, config)];
		            keys.forEach(key => {
		              rowsRef.current.delete(key);
		              pacesRef.current.delete(key);
		              gexByStrikeRef.current.delete(key);
		              strikeFlowRef.current.delete(key);
		            });
	            setLatestSnapshot(current => Number(current?.strike) === strike ? undefined : current);
	            setSelectedSpread(current => Number(current?.strike) === strike ? undefined : current);
	            bumpRows();
				          } else if (message.type === 'pace') {
			            if (!shouldAcceptStreamPayload(payload, config)) return;
			            if (mergePacePayload(payload)) {
			              bumpRows();
			            }
			          } else if (message.type === 'gex-by-strike') {
			            if (!shouldAcceptStreamPayload(payload, config)) return;
			            if (mergeGexPayload(payload)) {
			              bumpRows();
			            }
                      } else if (message.type === 'strike-flow') {
                        if (!shouldAcceptStrikeFlowPayload(payload, config)) return;
                        if (mergeStrikeFlowPayload(payload)) {
                          bumpRows();
                        }
					                          } else if (message.type === 'directional-pressure') {
				                            if (!shouldAcceptStreamPayload(payload, config)) return;
				                            setDirectionalPressure(payload);
					                          } else if (message.type === 'vix-price') {
				                            if (shouldAcceptIndexPricePayload(payload, config, 'VIX')) setVixPrice(payload);
					                          } else if (message.type === 'index-price') {
				                            if (shouldAcceptIndexPricePayload(payload, config, ['ESM6', 'ES.V.0'])) setEsPrice(payload);
					                          } else if (message.type === 'volume-sandwich') {
			                            if (!shouldAcceptStreamPayload(payload, config)) return;
			                            applyVolumeSandwichPayload(payload);
			                          } else if (message.type === 'volume-sandwich-alert') {
			                            const alert = payload.alert || payload;
			                            if (!shouldAcceptStreamPayload(alert, config)) return;
			                            applyVolumeSandwichAlertPayload(alert);
                              } else if (message.type === 'hpsf-latest-signal') {
                                applyHpsfViews({ latestSignal: payload });
                              } else if (message.type === 'hpsf-market-flow') {
                                applyHpsfViews({ marketFlow: payload });
                              } else if (message.type === 'hpsf-top-candidates') {
                                applyHpsfViews({ topCandidates: payload });
                              } else if (message.type === 'hpsf-audit') {
                                applyHpsfViews({ audit: payload });
                              } else if (message.type === 'hpsf-exit-intent') {
                                applyHpsfViews({ exitIntent: payload });
                              } else if (message.type === 'hpsf-validation') {
                                applyHpsfViews({ validation: payload });
		                          } else if (message.type === 'error') {
	            setStatus({ text: 'Failed', mode: 'error' });
	            setNotice(payload.error || 'Stream error');
          }
        };

        socket.onerror = () => {
          setStatus({ text: 'Feed unavailable', mode: 'error' });
          setNotice(feedUnavailableNotice(config));
          clearData();
        };

        socket.onclose = () => {
          if (wsRef.current === socket) {
            wsRef.current = undefined;
          }
          if (!stopped) {
            setStatus({ text: 'Feed unavailable', mode: 'error' });
            setNotice(feedUnavailableNotice(config));
            clearData();
            reconnectTimerRef.current = window.setTimeout(openSocket, 1500);
          }
        };
      };

      openSocket();
      return () => {
        stopped = true;
        if (reconnectTimerRef.current) {
          window.clearTimeout(reconnectTimerRef.current);
        }
        if (wsRef.current) {
          wsRef.current.close();
        }
      };
	    }, [applyConfigState, applyHpsfViews, applyUiBatch, applyVolumeSandwichAlertPayload, applyVolumeSandwichPayload, bumpRows, clearData, config, configReady, mergeGexPayload, mergePacePayload, mergeSnapshotPayload, mergeStrikeFlowPayload]);

    useEffect(() => () => {
      centerTimersRef.current.forEach(window.clearTimeout);
      centerTimersRef.current = [];
      cancelScheduledRowRender();
      if (hpsfPulseTimerRef.current !== undefined) {
        window.clearTimeout(hpsfPulseTimerRef.current);
        hpsfPulseTimerRef.current = undefined;
      }
    }, [cancelScheduledRowRender]);

    useEffect(() => {
      const tick = () => setAmsterdamTime(amsterdamTimeLabel());
      tick();
      const timer = window.setInterval(tick, 1000);
      return () => window.clearInterval(timer);
    }, []);

    useEffect(() => {
      const timer = window.setInterval(() => {
        if (gexByStrikeRef.current.size > 0) setRowsVersion(version => version + 1);
      }, 10_000);
      return () => window.clearInterval(timer);
    }, []);

    const cachedData = useMemo(
      () => [...rowsRef.current.values()].sort((a, b) => Number(a.strike) - Number(b.strike)),
      [rowsVersion]
	    );
	    const spot = latestSnapshot?.underlyingPrice ?? cachedData[0]?.underlyingPrice;
    const visibleMaxStrikes = isDatabentoMarketData(config) ? cachedData.length : form.maxStrikes ?? config.maxStrikes;
	    const data = useMemo(
	      () => stableActiveStrikeRows(cachedData, spot, visibleMaxStrikes, visibleStrikesRef),
	      [cachedData, spot, visibleMaxStrikes]
	    );
	    const paceFields = useMemo(() => paceWindowFields(paceWindow), [paceWindow]);
	    const atmIndex = useMemo(() => nearestStrikeIndex(data, spot), [data, spot]);
    const atmStrike = atmIndex >= 0 ? data[atmIndex].strike : undefined;
    const totalCallVolume = totalVolumeForSide(cachedData, 'call');
    const totalPutVolume = totalVolumeForSide(cachedData, 'put');
    const totalPace = useMemo(() => totalPaceForWindow(data, paceFields), [data, paceFields]);
    const topCallVolumeStrikes = useMemo(() => topVolumeStrikes(data, 'call'), [data]);
    const topPutVolumeStrikes = useMemo(() => topVolumeStrikes(data, 'put'), [data]);
    const strikeFlowLeaders = useMemo(() => strikeFlowLeaderboard(cachedData), [cachedData]);
	    const maxCallPace = Math.max(0, ...data.map(row => Number(row[paceFields.call] || 0)));
	    const maxPutPace = Math.max(0, ...data.map(row => Number(row[paceFields.put] || 0)));
			                    const blinkCallStrike = highestPaceStrike(data, paceFields.call);
			                    const blinkPutStrike = highestPaceStrike(data, paceFields.put);
			                    const selectedTicket = useMemo(() => selectedSpreadTicket(data, selectedSpread), [data, selectedSpread]);
			                    const gexSummary = useMemo(() => unusualWhalesGexSummary(data), [data]);
			                    const maxAbsGex = useMemo(() => maxAbsNetGex(data), [data]);
			                    const feedUnavailable = status.text === 'Feed unavailable';
			                    const databentoMode = isDatabentoMarketData(config);
                            const gexSourceLabel = databentoMode ? 'DBN' : 'UW';
                            const gexSourceName = databentoMode ? 'databento' : 'unusual-whales';
                            const gexStatus = computeGexStatus(databentoMode, data.length, gexSummary);
                            const gexStatusClass = gexStatus.className;
                            const gexStatusLabel = gexStatus.label;
                            const gexOptional = gexStatus.state === 'optional';
		                    const subtitle = config.symbol
        ? `${config.symbol} ${formatExpiry(config.expiry)} | ${String(config.marketDataSource || '').toUpperCase()} market data | ${String(config.provider || '').toUpperCase()} orders`
        : 'Loading config...';
    const selectedExpiryDate = formatExpiryDisplay(config.expiry);
    const gexStatusJson = JSON.stringify({
      source: gexSourceName,
      sourceLabel: gexSourceLabel,
      marketDataSource: config.marketDataSource,
      expiry: normalizeExpiry(config.expiry),
      date: selectedExpiryDate,
      state: gexStatus.state,
      reason: gexStatus.reason,
      visibleStrikeCount: data.length,
      visibleGexCount: gexSummary.visibleCount,
      staleGexCount: gexSummary.staleCount,
      missingGexCount: gexSummary.missingCount,
      disabled: gexStatus.state === 'optional',
      optional: gexStatus.state === 'optional',
      unavailable: gexStatus.state === 'unavailable'
    });

    useEffect(() => {
      if (!didInitialCenterRef.current && data.length > 0 && atmIndex >= 0 && Number.isFinite(Number(spot))) {
        didInitialCenterRef.current = true;
        centerCurrentStrike(centerTimersRef.current, tableWrapRef.current, tableBodyRef.current, atmIndex, false);
      }
    }, [atmIndex, data.length, spot]);

    const updateForm = (name, value) => setForm(current => ({ ...current, [name]: value }));
    const reconnect = async (overrides = {}) => {
      const requestForm = { ...form, ...overrides };
      setConnectPending(true);
      setStatus({ text: 'Connecting', mode: '' });
      setNotice(`Connecting ${requestForm.marketDataSource} market data for ${requestForm.symbol} ${formatExpiry(requestForm.expiry)}...`);
      const body = new URLSearchParams({
        provider: requestForm.provider,
        marketDataSource: requestForm.marketDataSource,
        symbol: requestForm.symbol,
        expiry: toIbExpiry(requestForm.expiry),
        port: requestForm.port,
        clientId: requestForm.clientId,
        maxStrikes: requestForm.maxStrikes,
        delayed: requestForm.delayed ? 'true' : 'false'
      });

      try {
        const response = await fetch(apiUrl('/api/connect'), { method: 'POST', body });
        if (!response.ok) {
          const payload = await response.json().catch(() => ({ error: 'Connection failed' }));
          throw new Error(payload.error || 'Connection failed');
        }
        const payload = await response.json();
        if (payload.config) applyConfigState(payload.config);
        setNotice('');
      } catch (error) {
        setStatus({ text: 'Failed', mode: 'error' });
        setNotice(error.message);
      } finally {
        setConnectPending(false);
	                      }
	                    };

		                    const submitSelectedOrder = async () => {
		                      if (!selectedTicket) {
		                        setNotice('Select a call or put spread before staging an IBKR order.');
		                        return;
		                      }
		                      if (!Number.isFinite(selectedTicket.credit)) {
		                        setNotice(`${selectedTicket.strategy} ${selectedTicket.shortStrike}/${selectedTicket.longStrike} does not have a valid midpoint credit yet.`);
		                        return;
		                      }
		                      setOrderPending(true);
	                      setNotice(`Staging ${selectedTicket.strategy} ${selectedTicket.shortStrike}/${selectedTicket.longStrike}...`);
	                      try {
	                        const response = await fetch(apiUrl('/api/orders/spread'), {
	                          method: 'POST',
	                          headers: { 'Content-Type': 'application/json' },
	                          body: JSON.stringify({
	                            side: selectedTicket.side,
	                            strike: selectedTicket.strike,
	                            quantity: Number(orderQuantity || 1),
	                            credit: selectedTicket.credit,
	                            symbol: config.symbol,
	                            expiry: config.expiry
	                          })
	                        });
	                        const payload = await response.json().catch(() => ({}));
	                        if (!response.ok) {
	                          throw new Error(payload.error || 'Could not stage IBKR order');
	                        }
	                        setNotice(`Staged ${payload.strategy} order ${payload.orderId}: ${payload.shortStrike}/${payload.longStrike} credit ${fmtCredit(payload.creditLimit)} IB ${fmtPrice(payload.ibLimitPrice)} status ${payload.status}`);
	                      } catch (error) {
	                        setNotice(error.message);
	                      } finally {
	                        setOrderPending(false);
	                      }
	                    };

	                    return h('main', { className: 'chain-shell' },
			                      h('header', { className: 'chain-header' },
	        h('a', { className: 'back-link', href: '/' }, 'OptionsEdge'),
	        h('span', { id: 'connectionState', className: `status-pill ${status.mode}`.trim() }, status.text),
	        h('span', { id: 'subtitle', className: 'subtitle' }, subtitle),
	        h('span', {
	          id: 'selectedExpiryDate',
	          className: 'selected-expiry-date',
	          'data-expiry': normalizeExpiry(config.expiry)
	        }, `Date ${selectedExpiryDate}`),
	        h('span', {
	          id: 'unusualWhalesGexStatus',
	          className: `gex-status ${gexStatusClass}`.trim(),
	          title: gexStatus.reason || gexStatusLabel,
	          'data-gex-source': gexSourceName,
	          'data-gex-state': gexStatus.state,
	          'data-gex-json': gexStatusJson,
	          'data-uw-gex-json': gexStatusJson
	        }, gexStatusLabel),
	        h('span', { id: 'rowCount' }, `${data.length.toLocaleString()} records`)
				                      ),
				                      h(Controls, { form, updateForm, reconnect, connectPending }),
				                      notice ? h('div', { className: `feed-notice ${feedUnavailable ? 'unavailable' : ''}`.trim() }, notice) : null,
                              h(HpsfDashboard, { hpsfState, pulse: hpsfPulse, spotFallback: spot, config, status }),
						                      h(SummaryGrid, { spot, vixPrice, esPrice, totalCallVolume, totalPutVolume, totalPace, paceWindow, directionalPressure, pressureWindow, onPressureWindowChange: setPressureWindow }),
                              h(StrikeFlowLeaderboard, { leaders: strikeFlowLeaders }),
	                      h('section', { className: 'chain-card' },
        h('div', { className: 'chain-toolbar' },
          h('div', null,
            h('strong', { id: 'atmLabel' }, `ATM: ${atmStrike ?? '--'}`),
            h('strong', { id: 'spotInline', className: 'spot-inline' }, `Spot: ${spot === undefined ? '--' : fmtPrice(spot)}`),
            h('span', null, 'Call and put volumes highlight their own fastest pace strike.')
          ),
	                          h('div', { className: 'legend' },
		                            h('button', {
		                              id: 'centerSpot',
              className: 'center-spot',
              type: 'button',
              onClick: () => centerCurrentStrike(centerTimersRef.current, tableWrapRef.current, tableBodyRef.current, atmIndex, true)
			                            }, 'Center Spot'),
			                            h(PaceWindowSelect, { value: paceWindow, onChange: setPaceWindow }),
		                            h('span', null, h('i', { className: 'legend-call' }), ' Call pace'),
	                            h('span', null, h('i', { className: 'legend-put' }), ' Put pace')
	                          ),
	                          h(OrderTicket, {
	                            selectedTicket,
	                            provider: config.provider,
	                            quantity: orderQuantity,
	                            setQuantity: setOrderQuantity,
	                            orderPending,
	                            submitSelectedOrder
	                          })
	                        ),
	                        h(ChainTable, {
	                          data,
	                          spot,
	                          atmStrike,
	                          blinkCallStrike,
	                          blinkPutStrike,
		                          topCallVolumeStrikes,
          topPutVolumeStrikes,
	                          maxCallPace,
          maxPutPace,
		                          paceFields,
		                          paceWindow,
			                          tableWrapRef,
		                          tableBodyRef,
		                          selectedSpread,
				                          onSelectSpread: setSelectedSpread,
					                          volumeSandwichState,
					                          amsterdamTime,
					                          maxAbsGex,
					                          feedUnavailable,
                              configReady
					                        })
	                      )
	                    );
  }

				                  function Controls({ form, updateForm, reconnect, connectPending }) {
    const submitOnEnter = event => {
      if (event.key === 'Enter') reconnect();
    };
    return h('section', { className: 'controls', 'aria-label': 'Connection controls' },
      h('label', null, 'Provider',
        h('select', { value: form.provider, onChange: event => updateForm('provider', event.target.value) },
          h('option', { value: 'IB' }, 'IB'),
          h('option', { value: 'MOCK' }, 'MOCK')
        )
      ),
      h('label', null, 'Market Data',
        h('select', { id: 'marketDataSource', value: form.marketDataSource, onChange: event => {
          const marketDataSource = event.target.value;
          const nextForm = { ...form, marketDataSource };
          updateForm('marketDataSource', marketDataSource);
          reconnect(nextForm);
        } },
          h('option', { value: 'IBKR' }, 'IBKR'),
          h('option', { value: 'DATABENTO' }, 'DATABENTO')
        )
      ),
      h('label', null, 'Symbol',
        h('input', { type: 'text', maxLength: 12, value: form.symbol || '', onChange: event => updateForm('symbol', event.target.value.toUpperCase()), onKeyDown: submitOnEnter })
      ),
      h('label', null, 'Expiry',
        h('input', { type: 'date', value: toDateInput(form.expiry), min: marketTodayIso(), onChange: event => {
          const expiry = nextWeekdayExpiry(toIbExpiry(event.target.value));
          const nextForm = { ...form, expiry };
          updateForm('expiry', expiry);
          if (expiry.length === 8) reconnect(nextForm);
        }, onKeyDown: submitOnEnter })
      ),
      h('label', null, 'Port',
        h('input', { type: 'number', min: '1', value: form.port ?? '', onChange: event => updateForm('port', event.target.value), onKeyDown: submitOnEnter })
      ),
      h('label', null, 'ClientId',
        h('input', { type: 'number', min: '0', value: form.clientId ?? '', onChange: event => updateForm('clientId', event.target.value), onKeyDown: submitOnEnter })
      ),
      h('label', null, 'Max Strikes',
        h('input', { type: 'number', min: '1', value: form.maxStrikes ?? '', onChange: event => updateForm('maxStrikes', event.target.value), onKeyDown: submitOnEnter })
      ),
      h('label', { className: 'check' },
        h('input', { type: 'checkbox', checked: Boolean(form.delayed), onChange: event => updateForm('delayed', event.target.checked) }),
        'Delayed'
      ),
      h('button', { id: 'connect', type: 'button', disabled: connectPending, onClick: reconnect }, connectPending ? 'Connecting' : 'Connect')
    );
	  }

                  function PaceWindowSelect({ value, onChange }) {
                    return h('label', { className: 'pace-window-control' }, 'Pace',
                      h('select', {
                        id: 'paceWindow',
                        className: 'pace-window-select',
                        value,
                        onChange: event => onChange(event.target.value)
                      },
                        h('option', { value: '10s' }, '10s'),
                        h('option', { value: '1m' }, '1m'),
                        h('option', { value: '10m' }, '10m'),
                        h('option', { value: '15m' }, '15m'),
                        h('option', { value: '30m' }, '30m'),
                        h('option', { value: '1h' }, '1h'),
                        h('option', { value: '4h' }, '4h'),
                        h('option', { value: '8h' }, '8h')
                      )
                    );
                  }

                  function PressureWindowSelect({ value, onChange }) {
                    return h('label', { className: 'pace-window-control pressure-window-control' }, 'Pressure',
                      h('select', {
                        id: 'pressureWindow',
                        className: 'pace-window-select pressure-window-select',
                        value,
                        onChange: event => onChange(event.target.value)
                      },
                        h('option', { value: '1m' }, '1m'),
                        h('option', { value: '5m' }, '5m'),
                        h('option', { value: '10m' }, '10m'),
                        h('option', { value: '15m' }, '15m'),
                        h('option', { value: '30m' }, '30m'),
                        h('option', { value: '1h' }, '1h'),
                        h('option', { value: '4h' }, '4h'),
                        h('option', { value: '8h' }, '8h')
                      )
	                    );
	                  }

                  function HpsfDashboard({ hpsfState, pulse, spotFallback, config, status }) {
                    const signal = hpsfState.latestSignal || hpsfFallbackSignal(status);
                    const displayState = hpsfDisplayState(signal);
                    const dataUnsafe = displayState === 'DATA_STALE';
                    const mixedFlow = displayState === 'MIXED_FLOW' || hpsfState.marketFlow?.mixedFlow === true;
                    return h('section', { className: 'hpsf-dashboard', 'aria-label': 'HPSF V2 signal dashboard' },
                      dataUnsafe ? h('div', { className: 'hpsf-alert hpsf-alert-danger' }, 'DATA NOT SAFE - NO BUY SIGNALS') : null,
                      mixedFlow ? h('div', { className: 'hpsf-alert hpsf-alert-warn' }, 'MIXED FLOW - NO TRADE') : null,
                      h(HpsfDataHealthBar, { signal, marketFlow: hpsfState.marketFlow, config, status }),
                      h('div', { className: 'hpsf-top-grid' },
                        h(HpsfSignalCard, { signal, pulse, spotFallback }),
                        h(HpsfMarketPanel, { marketFlow: hpsfState.marketFlow, signal })
                      ),
                      h(HpsfExecutionAnchorGrid, { signal, topCandidates: hpsfState.topCandidates }),
                      h('div', { className: 'hpsf-middle-grid' },
                        h(HpsfCandidateTables, { topCandidates: hpsfState.topCandidates }),
                        h(HpsfVwapTimeline, { signal, marketFlow: hpsfState.marketFlow })
                      ),
                      h('div', { className: 'hpsf-bottom-grid' },
                        h(HpsfAuditPanel, { signal, audit: hpsfState.audit }),
                        h(HpsfValidationPanel, { validation: hpsfState.validation, exitIntent: hpsfState.exitIntent })
                      )
                    );
                  }

                  function HpsfSignalCard({ signal, pulse, spotFallback }) {
                    const displayState = hpsfDisplayState(signal);
                    const spec = hpsfActionSpec(displayState);
                    const colorClass = signal.colorClass || spec.colorClass;
                    const strikeText = signal.executionText || hpsfExecutionText(signal);
                    const confidence = Number(signal.confidence || 0);
                    const reasons = hpsfReasons(signal);
                    return h('article', { className: `hpsf-signal-card ${colorClass} ${pulse ? 'pulse-once' : ''}`.trim() },
                      h('div', { className: 'hpsf-signal-heading' },
                        h('div', null,
                          h('div', { className: 'hpsf-signal-title' }, `${spec.icon} ${signal.title || spec.label}`),
                          h('div', { className: 'hpsf-setup' }, `Setup: ${hpsfText(signal.setup || 'WAITING_FOR_HPSF')}`)
                        ),
                        h('div', { className: 'hpsf-confidence-badge' }, `${Math.round(confidence)}%`)
                      ),
                      h('div', { className: 'hpsf-execution-strike' }, strikeText || '--'),
                      h('div', { className: 'hpsf-signal-meta' },
                        h('span', null, `Spot ${fmtHpsfNumber(signal.spot ?? spotFallback)}`),
                        h('span', null, `VWAP ${fmtHpsfNumber(signal.vwap)}`),
                        h('span', null, `Dist ${fmtSignedHpsfNumber(signal.distanceToVwap)}`)
                      ),
                      h('div', { className: 'hpsf-target-line' },
                        h('span', null, `Flow Anchor: ${fmtHpsfNumber(signal.flowAnchorStrike)}`),
                        h('span', null, `Target Zone: ${fmtHpsfNumber(signal.targetZoneLow)} - ${fmtHpsfNumber(signal.targetZoneHigh)}`)
                      ),
                      h('div', { className: 'hpsf-risk-line' }, signal.riskText || firstValue(reasons) || 'Waiting for latest signal rationale')
                    );
                  }

                  function HpsfMarketPanel({ marketFlow, signal }) {
                    const bull = Number(marketFlow?.bullishMarketScore ?? signal?.marketScore ?? 0);
                    const bear = Number(marketFlow?.bearishMarketScore ?? 0);
                    const mixed = marketFlow?.mixedFlow === true || hpsfDisplayState(signal) === 'MIXED_FLOW';
                    return h('article', { className: 'hpsf-market-card' },
                      h('div', { className: 'hpsf-panel-title' }, 'MARKET BIAS'),
                      h('div', { className: 'hpsf-score-bars' },
                        h(HpsfScoreBar, { label: 'Bull', value: bull, side: 'bull' }),
                        h(HpsfScoreBar, { label: 'Bear', value: bear, side: 'bear' })
                      ),
                      h('div', { className: 'hpsf-market-lines' },
                        h('span', null, `Bias: ${hpsfText(marketFlow?.marketBias || signal?.setup || '--')}`),
                        h('span', { className: mixed ? 'hpsf-warning-text' : '' }, `Mixed Flow: ${mixed ? 'YES' : 'No'}`),
                        h('span', null, `Call Flow 1m: ${fmtHpsfUsd(marketFlow?.bullishPremiumFlow1m)}`),
                        h('span', null, `Put Flow 1m: ${fmtHpsfUsd(marketFlow?.bearishPremiumFlow1m)}`),
                        h('span', null, `VWAP Reclaim: ${fmtHpsfScore(marketFlow?.vwapReclaimScore)}`),
                        h('span', null, `VWAP Breakdown: ${fmtHpsfScore(marketFlow?.vwapBreakdownScore)}`)
                      )
                    );
                  }

                  function HpsfScoreBar({ label, value, side }) {
                    const score = clampPercent(value);
                    return h('div', { className: `hpsf-score-bar hpsf-score-${side}` },
                      h('div', { className: 'hpsf-score-label' }, h('span', null, label), h('b', null, `${score}`)),
                      h('div', { className: 'hpsf-score-track' },
                        h('span', { className: 'hpsf-score-fill', style: { height: `${score}%` } })
                      )
                    );
                  }

                  function HpsfDataHealthBar({ signal, marketFlow, config, status }) {
                    const displayState = hpsfDisplayState(signal);
                    const unsafe = displayState === 'DATA_STALE';
                    const liveText = unsafe ? 'DATA NOT SAFE' : marketFlow?.dataHealth || signal.dataHealth || status?.text || 'WARMING_UP';
                    const mode = signal.mode || config?.hpsfMode || config?.mode || 'SHADOW';
                    const pills = [
                      ['OPRA', unsafe ? 'STALE' : 'LIVE'],
                      ['ES', unsafe ? 'STALE' : 'LIVE'],
                      ['SPX', unsafe ? 'STALE' : 'LIVE'],
                      ['VWAP', unsafe ? 'STALE' : 'OK'],
                      ['BASIS', unsafe ? 'STALE' : 'OK'],
                      ['CHAIN', unsafe ? 'CHECK' : 'OK'],
                      ['MODE', mode],
                      ['HPSF', liveText]
                    ];
                    return h('div', { className: 'hpsf-data-health-bar' },
                      h('span', { className: 'hpsf-health-title' }, 'HPSF DATA HEALTH'),
                      ...pills.map(([label, value]) => h('span', { key: label, className: `hpsf-badge ${unsafe ? 'danger' : hpsfHealthClass(value)}` },
                        h('b', null, label),
                        ' ',
                        h('span', null, value)
                      ))
                    );
                  }

                  function HpsfExecutionAnchorGrid({ signal, topCandidates }) {
                    const executionCandidate = hpsfFindCandidate(topCandidates, signal.executionStrike, signal.selectedOptionType, 'EXECUTION');
                    const anchorCandidate = hpsfFindCandidate(topCandidates, signal.flowAnchorStrike, signal.selectedOptionType, 'ANCHOR');
                    return h('div', { className: 'hpsf-execution-anchor-grid' },
                      h(HpsfExecutionCard, { signal, candidate: executionCandidate }),
                      h('div', { className: 'hpsf-flow-arrow', 'aria-hidden': 'true' }, '->'),
                      h(HpsfAnchorCard, { signal, candidate: anchorCandidate })
                    );
                  }

                  function HpsfExecutionCard({ signal, candidate }) {
                    return h('article', { className: `hpsf-execution-card ${hpsfOptionSideClass(signal.selectedOptionType)}` },
                      h('div', { className: 'hpsf-panel-title' }, 'EXECUTION STRIKE'),
                      h('strong', null, hpsfExecutionText(signal)),
                      h('div', { className: 'hpsf-card-grid' },
                        h(HpsfMiniMetric, { label: 'Score', value: fmtHpsfScore(candidate?.score ?? signal.strikeScore) }),
                        h(HpsfMiniMetric, { label: 'Bid / Ask', value: `${fmtHpsfNumber(candidate?.bid)} / ${fmtHpsfNumber(candidate?.ask)}` }),
                        h(HpsfMiniMetric, { label: 'Spread', value: fmtHpsfPct(candidate?.spreadPct) }),
                        h(HpsfMiniMetric, { label: 'Speed', value: fmtHpsfMultiple(candidate?.volumeSpeed) }),
                        h(HpsfMiniMetric, { label: 'Liquidity', value: candidate?.liquidityOk === false ? 'BAD' : 'OK' }),
                        h(HpsfMiniMetric, { label: 'Distance', value: candidate?.candidateDistanceOk === false ? 'BAD' : 'OK' })
                      )
                    );
                  }

                  function HpsfAnchorCard({ signal, candidate }) {
                    return h('article', { className: 'hpsf-anchor-card' },
                      h('div', { className: 'hpsf-panel-title' }, 'FLOW ANCHOR / TARGET'),
                      h('strong', null, `${fmtHpsfNumber(signal.flowAnchorStrike)} ${signal.selectedOptionType || ''}`.trim() || '--'),
                      h('div', { className: 'hpsf-card-grid' },
                        h(HpsfMiniMetric, { label: 'Score', value: fmtHpsfScore(candidate?.score) }),
                        h(HpsfMiniMetric, { label: 'Target Zone', value: `${fmtHpsfNumber(signal.targetZoneLow)} - ${fmtHpsfNumber(signal.targetZoneHigh)}` }),
                        h(HpsfMiniMetric, { label: 'Net Premium', value: fmtHpsfUsd(candidate?.netPremium) }),
                        h(HpsfMiniMetric, { label: 'Role', value: hpsfText(candidate?.candidateRole || 'ANCHOR') })
                      ),
                      h('p', null, 'Buy the execution strike; use the flow anchor as the target area when it is too far from spot.')
                    );
                  }

                  function HpsfMiniMetric({ label, value }) {
                    return h('span', { className: 'hpsf-mini-metric' }, h('small', null, label), h('b', null, value || '--'));
                  }

                  function HpsfCandidateTables({ topCandidates }) {
                    return h('div', { className: 'hpsf-candidates-grid' },
                      h(HpsfCandidateTable, { title: 'Top Call Candidates', side: 'CALL', rows: hpsfCandidateRows(topCandidates, 'CALL') }),
                      h(HpsfCandidateTable, { title: 'Top Put Candidates', side: 'PUT', rows: hpsfCandidateRows(topCandidates, 'PUT') })
                    );
                  }

                  function HpsfCandidateTable({ title, side, rows }) {
                    const bodyRows = rows.length ? rows : [{ strike: '--', candidateRole: 'WAITING', score: undefined, netPremium: undefined, volumeSpeed: undefined, spreadPct: undefined, liquidityOk: undefined, reason: 'Waiting for gateway HPSF candidates' }];
                    return h('article', { className: `hpsf-candidate-table hpsf-${side.toLowerCase()}` },
                      h('div', { className: 'hpsf-panel-title' }, title),
                      h('table', null,
                        h('thead', null, h('tr', null,
                          h('th', null, '#'),
                          h('th', null, 'Strike'),
                          h('th', null, 'Role'),
                          h('th', null, 'Score'),
                          h('th', null, 'Net $'),
                          h('th', null, 'Speed'),
                          h('th', null, 'Spread'),
                          h('th', null, 'OK')
                        )),
                        h('tbody', null,
                          bodyRows.slice(0, 6).map((row, index) => h('tr', { key: `${side}-${row.strike}-${row.candidateRole}-${index}`, className: hpsfCandidateClass(row) },
                            h('td', null, index + 1),
                            h('td', null, row.strike),
                            h('td', null, h(HpsfRoleBadge, { role: row.candidateRole })),
                            h('td', null, fmtHpsfScore(row.score)),
                            h('td', null, fmtHpsfUsd(row.netPremium)),
                            h('td', null, fmtHpsfMultiple(row.volumeSpeed)),
                            h('td', null, fmtHpsfPct(row.spreadPct)),
                            h('td', null, hpsfCandidateOk(row))
                          ))
                        )
                      )
                    );
                  }

                  function HpsfRoleBadge({ role }) {
                    const text = hpsfText(role || '--');
                    const roleClass = String(role || '').toUpperCase().includes('ANCHOR') ? 'anchor' : String(role || '').toUpperCase().includes('EXEC') ? 'execution' : 'warn';
                    return h('span', { className: `hpsf-badge ${roleClass}` }, text);
                  }

                  function HpsfVwapTimeline({ signal, marketFlow }) {
                    const path = hpsfVwapPath(signal, marketFlow);
                    return h('article', { className: 'hpsf-vwap-timeline' },
                      h('div', { className: 'hpsf-panel-title' }, path.title),
                      h('div', { className: 'hpsf-timeline-track' },
                        path.steps.map(step => h('div', { key: step.label, className: `hpsf-step ${step.state}`.trim() },
                          h('span', { className: 'hpsf-step-icon' }, step.icon),
                          h('b', null, step.label),
                          h('small', null, step.detail || '')
                        ))
                      ),
                      h('div', { className: 'hpsf-timeline-meta' },
                        h('span', null, `State: ${hpsfText(marketFlow?.internalVwapState || signal.internalVwapState || '--')}`),
                        h('span', null, `VWAP ${fmtHpsfNumber(signal.vwap)}`),
                        h('span', null, `Dist ${fmtSignedHpsfNumber(signal.distanceToVwap)}`)
                      )
                    );
                  }

                  function HpsfAuditPanel({ signal, audit }) {
                    const reasons = hpsfReasons(signal);
                    const rejected = Array.isArray(audit?.rejectedActions) ? audit.rejectedActions : [];
                    const gates = Array.isArray(audit?.noTradeGates) ? audit.noTradeGates : [];
                    return h('article', { className: 'hpsf-audit-panel' },
                      h('div', { className: 'hpsf-panel-title' }, 'WHY THIS SIGNAL?'),
                      h('p', { className: 'hpsf-audit-selected' }, `Selected: ${signal.title || hpsfText(signal.action || 'NO_TRADE')}`),
                      h('ul', { className: 'hpsf-reason-list' },
                        (reasons.length ? reasons : ['Waiting for latest HPSF reason summary']).slice(0, 6).map((reason, index) => h('li', { key: `reason-${index}` }, h('span', null, '✓'), reason))
                      ),
                      rejected.length ? h('div', { className: 'hpsf-rejected-actions' },
                        h('b', null, 'Rejected Actions'),
                        rejected.slice(0, 4).map((item, index) => h('span', { key: `reject-${index}` }, hpsfRejectedText(item)))
                      ) : null,
                      h('div', { className: 'hpsf-gate-row' },
                        ['Mixed Flow', 'Liquidity', 'Distance', 'Source Freshness', 'Chain Coverage'].map(label => h('span', { key: label, className: 'hpsf-badge ok' }, `${label}: OK`)),
                        gates.slice(0, 4).map((gate, index) => h('span', { key: `gate-${index}`, className: 'hpsf-badge warn' }, hpsfText(gate.name || gate.reason || gate)))
                      )
                    );
                  }

                  function HpsfValidationPanel({ validation, exitIntent }) {
                    const exitAction = exitIntent?.exitAction || 'HOLD';
                    return h('article', { className: 'hpsf-validation-panel' },
                      h('div', { className: 'hpsf-panel-title' }, 'SHADOW / EXIT INTENT'),
                      validation
                        ? h('div', { className: 'hpsf-validation-grid' },
                            h(HpsfMiniMetric, { label: 'Entry', value: validation.entrySignal || '--' }),
                            h(HpsfMiniMetric, { label: 'Entry Ask', value: fmtHpsfNumber(validation.entryPrice) }),
                            h(HpsfMiniMetric, { label: 'MFE', value: fmtHpsfPct(validation.mfePct) }),
                            h(HpsfMiniMetric, { label: 'MAE', value: fmtHpsfPct(validation.maePct) }),
                            h(HpsfMiniMetric, { label: 'Outcome', value: hpsfText(validation.outcomeLabel || '--') })
                          )
                        : h('p', null, 'Waiting for delayed SHADOW/BACKTEST outcome. BUY signals remain informational only.'),
                      h('div', { className: 'hpsf-exit-intent' },
                        h('span', { className: `hpsf-badge ${exitAction === 'HOLD' ? 'ok' : 'danger'}` }, `Exit: ${hpsfText(exitAction)}`),
                        h('span', null, exitIntent?.reason || 'No exit intent')
                      )
                    );
                  }

				                  function SummaryGrid({ spot, vixPrice, esPrice, totalCallVolume, totalPutVolume, totalPace, paceWindow, directionalPressure, pressureWindow, onPressureWindowChange }) {
	    const ratioValue = putCallRatioValue(totalPutVolume, totalCallVolume);
	    const ratio = putCallRatioText(ratioValue);
	    const ratioLabel = putCallRatioLabel(ratioValue);
	    const paceRatioValue = totalPace?.call > 0 ? totalPace.put / totalPace.call : undefined;
	    const paceRatio = paceRatioValue === undefined ? '--' : paceRatioValue.toFixed(2);
	    return h('section', { className: 'summary-grid' },
	      h('article', { className: 'summary-card spot-card' }, h('span', null, 'Spot Price'), h('strong', { id: 'spotPrice' }, spot === undefined ? '--' : fmtPrice(spot))),
	      h(EsPriceCard, { esPrice }),
	      h(VixPriceCard, { vixPrice }),
	      h('article', { className: 'summary-card' }, h('span', null, 'Total Call Volume'), h('strong', { id: 'totalCallVolume', className: 'call-text' }, fmtSummaryVolume(totalCallVolume))),
      h('article', { className: 'summary-card' }, h('span', null, 'Total Put Volume'), h('strong', { id: 'totalPutVolume', className: 'put-text' }, fmtSummaryVolume(totalPutVolume))),
			                      h('article', { className: 'summary-card' }, h('span', null, 'Put/Call Volume Ratio'), h('strong', { id: 'putCallRatio', className: 'ratio-text' }, ratio), h('small', { id: 'putCallRatioLabel', className: 'put-call-ratio-label' }, ratioLabel)),
      h('article', { className: 'summary-card pace-total-card' }, h('span', null, `Put/Call Pace ${paceWindow}`), h('strong', { id: 'putCallPaceRatio', className: 'ratio-text' }, paceRatio), h('small', { id: 'putCallPaceTotals', className: 'pace-total-label' }, `Call ${fmtPace(totalPace?.call, paceWindow)} | Put ${fmtPace(totalPace?.put, paceWindow)}`)),
	                              h(PressureCard, { directionalPressure, pressureWindow, onPressureWindowChange })
					                    );
					                  }

	                  function VixPriceCard({ vixPrice }) {
	                    const price = Number(vixPrice?.price);
	                    const updated = vixPrice?.receivedAt || vixPrice?.eventTime || '';
	                    const quality = String(vixPrice?.quality || '--').toUpperCase();
	                    return h('article', {
	                      id: 'vixPriceCard',
	                      className: `summary-card vix-card ${quality === 'LIVE' ? 'live' : quality === 'DELAYED' ? 'delayed' : ''}`.trim()
	                    },
	                      h('span', null, 'VIX'),
	                      h('strong', { id: 'vixPrice' }, Number.isFinite(price) && price > 0 ? fmtPrice(price) : '--'),
	                      h('small', { id: 'vixPriceMeta', className: 'vix-price-meta' }, vixMetaText(quality, updated))
	                    );
	                  }

	                  function EsPriceCard({ esPrice }) {
	                    const price = Number(esPrice?.price);
	                    const updated = esPrice?.receiveTime || esPrice?.receivedAt || esPrice?.eventTime || '';
	                    const size = Number(esPrice?.size);
	                    return h('article', { id: 'esPriceCard', className: 'summary-card es-card live' },
	                      h('span', null, 'ESM6'),
	                      h('strong', { id: 'esPrice' }, Number.isFinite(price) && price > 0 ? fmtPrice(price) : '--'),
	                      h('small', { id: 'esPriceMeta', className: 'vix-price-meta' }, esMetaText(size, updated))
	                    );
	                  }

                  function PressureCard({ directionalPressure, pressureWindow, onPressureWindowChange }) {
                    const selectedPressure = directionalPressureForWindow(directionalPressure, pressureWindow);
                    const signal = selectedPressure?.signal || '--';
                    return h('article', {
                      id: 'optionsPressureCard',
                      className: `summary-card pressure-card ${pressureSignalClass(signal)}`.trim(),
                      tabIndex: 0
                    },
                      h('div', { className: 'pressure-card-title' },
                        h('span', null, 'Options Pressure'),
                        h(PressureWindowSelect, { value: pressureWindow, onChange: onPressureWindowChange })
                      ),
                      h('strong', { id: 'optionsPressure', className: 'pressure-signal' }, pressureSignalText(signal)),
                      h('div', { className: 'pressure-metrics' },
                        h('span', null, `Net: ${fmtSignedPressure(selectedPressure?.netScore)}`),
                        h('span', null, `Bull: ${fmtPressure(selectedPressure?.totalBullishPressure)}`),
                        h('span', null, `Bear: ${fmtPressure(selectedPressure?.totalBearishPressure)}`),
                        h('span', null, `Mode: ${selectedPressure?.thresholdMode || 'DEFAULT'}`)
                      ),
                      selectedPressure ? h(PressurePopover, { directionalPressure: selectedPressure }) : null
                    );
                  }

                  function PressurePopover({ directionalPressure }) {
                    return h('div', { className: 'pressure-popover', role: 'tooltip' },
                      h('strong', null, 'Top Pressure Strikes'),
                      h('div', { className: 'pressure-columns' },
                        h(PressureStrikeList, {
                          title: 'Bullish',
                          side: 'bullish',
                          strikes: directionalPressure.topCallPressureStrikes || []
                        }),
                        h(PressureStrikeList, {
                          title: 'Bearish',
                          side: 'bearish',
                          strikes: directionalPressure.topPutPressureStrikes || []
                        })
                      )
                    );
                  }

                  function PressureStrikeList({ title, side, strikes }) {
                    return h('div', { className: `pressure-list pressure-list-${side}` },
                      h('span', { className: 'pressure-list-title' }, title),
                      h('ol', null,
                        strikes.slice(0, 5).map(strike => {
                          const pressure = side === 'bullish'
                            ? (strike.bullishPressure ?? strike.callPressure)
                            : (strike.bearishPressure ?? strike.putPressure);
                          const flowPressure = side === 'bullish' ? strike.bullishFlowPressure : strike.bearishFlowPressure;
                          return h('li', { key: `${side}-${strike.strike}` },
                            h('span', { className: 'pressure-strike' }, strike.strike),
                            h('span', null, fmtPressure(pressure)),
                            h('span', null, `Flow ${fmtPressure(flowPressure)}`),
                            h('span', null, `OIw ${Number(strike.oiWeight || 1).toFixed(2)}`),
                            h('span', null, `Net ${fmtSignedPressure(strike.netPressure)}`),
                            h('span', null, `d ${fmtPrice(strike.distanceFromSpot)}`)
                          );
                        })
                      )
                    );
                  }

                  function StrikeFlowLeaderboard({ leaders }) {
                    const buy = leaders?.buy || [];
                    const sell = leaders?.sell || [];
                    if (!buy.length && !sell.length) return null;
                    return h('section', { className: 'strike-flow-leaderboard', 'aria-label': 'Strike Flow Leaderboard' },
                      h('div', { className: 'strike-flow-leaderboard-header' },
                        h('strong', null, 'Strike Flow Leaderboard'),
                        h('span', null, 'Institutional flow concentration')
                      ),
                      h('div', { className: 'strike-flow-leaderboard-grid' },
                        h(StrikeFlowLeaderColumn, { title: 'Buy Flow', side: 'buy', rows: buy, emptyText: 'Waiting for buy flow' }),
                        h(StrikeFlowLeaderColumn, { title: 'Sell Flow', side: 'sell', rows: sell, emptyText: 'Waiting for sell flow' })
                      )
                    );
                  }

                  function StrikeFlowLeaderColumn({ title, side, rows, emptyText }) {
                    return h('div', { className: `strike-flow-leader-column strike-flow-leader-${side}` },
                      h('div', { className: 'strike-flow-leader-title' },
                        h('span', null, title),
                        h('small', null, side === 'buy' ? 'Support / accumulation' : 'Resistance / distribution')
                      ),
                      rows.length
                        ? h('ol', null, rows.map(row => h('li', { key: `${side}-${row.rank}-${row.strike}`, title: row.title },
                          h('span', { className: 'strike-flow-rank' }, `${side.toUpperCase()} #${row.rank}`),
                          h('span', { className: 'strike-flow-strike' }, row.strike),
                          h('strong', { className: 'strike-flow-notional-value' }, row.notional),
                          h('small', { className: 'strike-flow-context' }, row.context)
                        )))
                        : h('p', { className: 'strike-flow-empty' }, emptyText)
                    );
                  }

			                  function OrderTicket({ selectedTicket, provider, quantity, setQuantity, orderPending, submitSelectedOrder }) {
			                    const canSubmit = Boolean(selectedTicket) && Number.isFinite(selectedTicket.credit) && String(provider || '').toUpperCase() === 'IB' && !orderPending;
		                    const label = selectedTicket
		                      ? `${selectedTicket.strategy} ${selectedTicket.shortStrike}/${selectedTicket.longStrike} ${Number.isFinite(selectedTicket.credit) ? `mid ${fmtCredit(selectedTicket.credit)}` : 'waiting for midpoint'}`
		                      : 'Select a call or put spread';
	                    return h('div', { className: 'order-ticket' },
	                      h('strong', { id: 'selectedOrderTicket' }, label),
	                      h('label', null, 'Qty',
	                        h('input', {
	                          id: 'orderQuantity',
	                          type: 'number',
	                          min: '1',
	                          max: '99',
	                          value: quantity,
	                          onChange: event => setQuantity(Math.max(1, Number(event.target.value || 1)))
	                        })
	                      ),
	                      h('button', {
	                        id: 'stageOrder',
	                        className: 'stage-order',
	                        type: 'button',
	                        disabled: !canSubmit,
	                        onClick: submitSelectedOrder
	                      }, orderPending ? 'Staging' : 'Stage IBKR')
	                    );
	                  }

				                  function ChainTable({ data, spot, atmStrike, blinkCallStrike, blinkPutStrike, topCallVolumeStrikes, topPutVolumeStrikes, maxCallPace, maxPutPace, paceFields, paceWindow, tableWrapRef, tableBodyRef, selectedSpread, onSelectSpread, volumeSandwichState, amsterdamTime, maxAbsGex, feedUnavailable, configReady }) {
		                    const tableStageRef = useRef(null);
		                    const [spotTop, setSpotTop] = useState(undefined);

		                    useLayoutEffect(() => {
		                      if (!data.length || !Number.isFinite(Number(spot))) {
		                        setSpotTop(undefined);
		                        return undefined;
		                      }

		                      let stopped = false;
		                      let frame = 0;
		                      const updateSpotTop = () => {
		                        if (stopped) return;
		                        const nextTop = spotLinePosition(data, spot, tableStageRef.current, tableBodyRef.current);
		                        setSpotTop(currentTop => samePosition(currentTop, nextTop) ? currentTop : nextTop);
		                      };
		                      const scheduleUpdate = () => {
		                        if (frame) window.cancelAnimationFrame(frame);
		                        frame = window.requestAnimationFrame(() => {
		                          frame = 0;
		                          updateSpotTop();
		                        });
		                      };

		                      updateSpotTop();
		                      scheduleUpdate();

		                      let observer;
		                      if (window.ResizeObserver && tableStageRef.current) {
		                        observer = new ResizeObserver(scheduleUpdate);
		                        observer.observe(tableStageRef.current);
		                        if (tableBodyRef.current) observer.observe(tableBodyRef.current);
		                      }

		                      return () => {
		                        stopped = true;
		                        if (frame) window.cancelAnimationFrame(frame);
		                        if (observer) observer.disconnect();
		                      };
		                    }, [data, spot, tableBodyRef]);

			                    if (!data.length) {
                          const emptyText = !configReady
                            ? 'Loading option chain config...'
                            : feedUnavailable
                              ? 'Feed is unavailable. Waiting for the feed gateway...'
                              : 'Waiting for option chain snapshots...';
		                      return h('div', { id: 'chainTableWrap', className: 'table-wrap', ref: tableWrapRef },
		                        h('div', { className: 'chain-empty' }, emptyText)
		                      );
		                    }
			                    const rowsByStrike = new Map(data.map(row => [Number(row.strike), row]));
			                    const sandwichCells = sandwichStrikeCells(volumeSandwichState?.currentTop2 || []);
	                    return h('div', { id: 'chainTableWrap', className: 'table-wrap', ref: tableWrapRef },
	                      h('div', { className: 'table-stage', ref: tableStageRef },
			                        Number.isFinite(spotTop)
			                          ? [
			                              h('div', { key: 'spot-line', id: 'spotLine', className: 'spot-line', style: { top: `${spotTop}px` }, title: `Spot ${fmtPrice(spot)} | Amsterdam ${amsterdamTime}`, 'aria-hidden': 'true' }),
			                              h('div', { key: 'spot-labels', id: 'spotLabels', className: 'spot-label-overlay', style: { top: `${spotTop}px` }, title: `Spot ${fmtPrice(spot)} | Amsterdam ${amsterdamTime}`, 'aria-hidden': 'true' },
			                                h('span', { className: 'spot-label-stack spot-label-call' },
			                                  h('span', { className: 'spot-price-label' }, fmtPrice(spot)),
			                                  h('span', { className: 'spot-time-label' }, amsterdamTime)
			                                ),
			                                h('span', { className: 'spot-label-stack spot-label-put' },
			                                  h('span', { className: 'spot-price-label' }, fmtPrice(spot)),
			                                  h('span', { className: 'spot-time-label' }, amsterdamTime)
			                                )
			                              )
			                            ]
			                          : null,
	                        h('table', { className: 'chain-table' },
	                          h('thead', null,
	                            h('tr', null,
	                              h('th', null, 'Call Mid'),
	                              h('th', null, 'Call Volume'),
	                              h('th', null, 'Call OI'),
	                              h('th', null, 'Strike'),
	                              h('th', null, 'Put OI'),
	                              h('th', null, 'Put Volume'),
	                              h('th', null, 'Put Mid')
	                            )
	                          ),
	                          h('tbody', { id: 'chainBody', ref: tableBodyRef },
		                            data.map(row => {
	                              const call = row.call || {};
	                              const put = row.put || {};
		                              const callLargeVolume = topCallVolumeStrikes.has(Number(row.strike));
		                              const putLargeVolume = topPutVolumeStrikes.has(Number(row.strike));
		                              const callPace = Number(row[paceFields.call] || 0);
		                              const putPace = Number(row[paceFields.put] || 0);
		                              const callPaceChange = Number(row[paceFields.callChange] || 0);
		                              const putPaceChange = Number(row[paceFields.putChange] || 0);
		                              const callPaceScore = paceScore(callPace, maxCallPace);
		                              const putPaceScore = paceScore(putPace, maxPutPace);
		                              const bearCallSpread = spreadDetails(row, rowsByStrike, 'call');
			                              const bullPutSpread = spreadDetails(row, rowsByStrike, 'put');
				                              const isCurrent = row.strike === atmStrike;
				                              const callSandwichCell = sandwichCells.get(sandwichCellKey(row.strike, 'call'));
				                              const putSandwichCell = sandwichCells.get(sandwichCellKey(row.strike, 'put'));
				                              const callStrikeFlow = strikeFlowVolumeState(row, 'call');
				                              const putStrikeFlow = strikeFlowVolumeState(row, 'put');
				                              const rowClasses = [isCurrent ? 'current-strike' : '', strikeFlowRowClass(row)].filter(Boolean).join(' ');
					                              const title = strikeRowTitle(row);
				                              return h('tr', { key: row.strike, className: rowClasses, 'data-strike': row.strike, title },
		                                h('td', spreadCellProps('call', row.strike, bearCallSpread, selectedSpread, onSelectSpread, callSandwichCell),
		                                  h('span', { className: 'price-with-credit' },
		                                    h('span', null, fmtPrice(midPrice(call))),
		                                    h('sup', { className: 'spread-credit' }, fmtCredit(bearCallSpread?.credit))
		                                  ),
		                                  sandwichRankBadge(callSandwichCell)
		                                ),
				                                h(VolumeCell, { volume: call.volume, largeVolume: callLargeVolume, pace: callPace, paceChange: callPaceChange, paceScore: callPaceScore, paceWindow, side: 'call', blink: row.strike === blinkCallStrike, strikeFlow: callStrikeFlow }),
				                                h(OpenInterestCell, { openInterest: call.openInterest, side: 'call' }),
						                                h(StrikeCell, { row, maxAbsGex }),
				                                h(OpenInterestCell, { openInterest: put.openInterest, side: 'put' }),
				                                h(VolumeCell, { volume: put.volume, largeVolume: putLargeVolume, pace: putPace, paceChange: putPaceChange, paceScore: putPaceScore, paceWindow, side: 'put', blink: row.strike === blinkPutStrike, strikeFlow: putStrikeFlow }),
		                                h('td', spreadCellProps('put', row.strike, bullPutSpread, selectedSpread, onSelectSpread, putSandwichCell),
		                                  h('span', { className: 'price-with-credit' },
		                                    h('span', null, fmtPrice(midPrice(put))),
		                                    h('sup', { className: 'spread-credit' }, fmtCredit(bullPutSpread?.credit))
		                                  ),
		                                  sandwichRankBadge(putSandwichCell)
		                                )
	                              );
	                            })
	                          )
	                        )
	                      )
	                    );
	                  }

			                  function VolumeCell({ volume, largeVolume, pace, paceChange, paceScore, paceWindow, side, blink, strikeFlow }) {
		                    const volumeClass = largeVolume ? ' large-volume' : '';
		                    const blinkClass = blink ? ' blink-volume' : '';
		                    const strikeFlowClass = strikeFlow?.className ? ` ${strikeFlow.className}` : '';
		                    const strikeFlowPulseClass = strikeFlow?.pulse ? ' strike-flow-pulse' : '';
		                    return h('td', {
                          className: `volume-cell ${side}-volume${volumeClass}${blinkClass}${strikeFlowClass}${strikeFlowPulseClass}`,
                          title: strikeFlow?.title || undefined
                        },
	                      h('span', { className: 'volume-wrap' },
	                        h('span', { className: 'volume-value' },
                            fmtVolume(volume),
                            strikeFlow?.superscriptNotional ? h('sup', { className: 'strike-flow-notional' }, strikeFlow.superscriptNotional) : null
                          ),
		                        paceIndicator(paceScore, pace, paceChange, side, paceWindow)
	                      )
			                    );
			                  }

			                  function OpenInterestCell({ openInterest, side }) {
		                    const label = side === 'put' ? 'Put' : 'Call';
		                    return h('td', { className: `open-interest-cell ${side}-open-interest`, title: `${label} open interest ${fmtVolume(openInterest)}` },
		                      h('span', { className: 'open-interest-value' }, fmtVolume(openInterest))
		                    );
		                  }

				                  function StrikeCell({ row, maxAbsGex }) {
				                    const gex = gexStrikeState(row, maxAbsGex);
				                    const gexExpiry = normalizeExpiry(row.uwGexExpiry || row.expiry);
				                    const historyJson = JSON.stringify(row.uwGexHistory || {});
				                    const timeframe = String(row.uwGexTimeframe || '1D').toUpperCase();
				                    return h('td', {
				                      className: `strike-cell ${gex.className}`.trim(),
				                      title: gex.title,
				                      style: gex.visible ? gex.style : undefined,
				                      'data-gex-source': gex.visible ? gexSourceMeta(row).name : '',
				                      'data-uw-gex-source': gex.visible ? gexSourceMeta(row).name : '',
				                      'data-uw-gex-timeframe': gex.visible ? timeframe : '',
				                      'data-uw-gex-expiry': gexExpiry,
				                      'data-uw-gex-date': formatExpiryDisplay(gexExpiry),
				                      'data-uw-gex-updated-at': row.uwGexUpdatedAt || '',
				                      'data-uw-gex-net': Number.isFinite(Number(row.uwNetGex)) ? String(Number(row.uwNetGex)) : '',
				                      'data-uw-gex-previous-net': Number.isFinite(Number(row.uwPreviousNetGex)) ? String(Number(row.uwPreviousNetGex)) : '',
				                      'data-uw-gex-change': Number.isFinite(Number(row.uwGexChange)) ? String(Number(row.uwGexChange)) : '',
				                      'data-uw-gex-strength': gex.visible ? String(gex.strengthPercent) : '',
				                      'data-uw-gex-move-direction': gex.visible ? gex.moveDirection : '',
				                      'data-uw-gex-gamma-sign': String(row.uwGammaSign || '').toUpperCase(),
				                      'data-uw-gex-history-json': historyJson,
				                      'data-uw-gex-stale': gex.stale ? 'true' : 'false'
				                    },
				                      gex.visible ? h('span', { className: 'gex-strength-fill', 'aria-hidden': 'true' }) : null,
				                      gex.visible ? h('span', { className: 'gex-strength-axis', 'aria-hidden': 'true' }) : null,
				                      gex.visible ? h('span', { className: `gex-strength-arrow ${gex.arrowClass}`, title: gex.changeTitle, 'aria-hidden': 'true' }, gex.arrow) : null,
				                      h('span', { className: 'strike-gex-content' },
				                        h('span', { className: 'strike-gex-line' },
				                          h('span', { className: 'strike-price-value' }, row.strike),
				                          gex.visible ? h('span', { className: 'uw-gex-value' }, gex.text) : null
				                        )
				                      ),
				                      gex.visible ? h(GexHistoryPopover, { row, gex }) : null,
				                      gex.stale ? h('sup', { className: 'gamma-stale-marker', title: `${gexSourceMeta(row).display} exposure is stale` }, '?') : null
			                    );
			                  }

			                  function GexHistoryPopover({ row, gex }) {
			                    const rows = gexHistoryRows(row);
				                    return h('div', { className: 'uw-gex-popover', role: 'tooltip' },
				                      h('strong', null, `${gexSourceMeta(row).display} ${String(row.uwGexTimeframe || '1D').toUpperCase()}`),
				                      h('span', { className: 'uw-gex-popover-strike' }, `Strike ${row.strike}`),
				                      h('span', { className: 'uw-gex-popover-meta' },
				                        `${row.symbol || '--'} ${formatExpiryDisplay(row.uwGexExpiry || row.expiry)}`
				                      ),
			                      h('span', { className: 'uw-gex-popover-updated' }, `Updated ${formatTime(row.uwGexUpdatedAt)}`),
			                      h('ul', null,
			                        h('li', null,
			                          h('span', null, 'Now:'),
			                          h('b', null, gex.text)
			                        ),
			                        ...rows.map(item => h('li', { key: item.window },
			                          h('span', null, `${item.window}:`),
			                          item.available
			                            ? [
			                                h('b', { key: 'value' }, item.valueText),
			                                h('em', { key: 'delta', className: `gex-delta ${item.deltaClass}` },
			                                  `${item.arrow} ${item.deltaText}`
			                                )
			                              ]
			                            : h('small', null, 'waiting for history')
			                        ))
			                      )
			                    );
			                  }

			                  function paceIndicator(score, pace, paceChange, side, paceWindow) {
		                    const percent = pacePercent(score);
		                    const label = side === 'put' ? 'Put' : 'Call';
		                    const paceText = fmtPace(pace, paceWindow);
		                    const paceChangeText = fmtPaceChange(paceChange, paceWindow);
		                    const paceChangeState = paceChangeClass(paceChange);
			                    return h('span', { className: `pace-meter pace-${side}`, title: `${label} pace ${paceWindow} ${paceText}; pace of pace ${paceChangeText}` },
		                      h('span', { className: 'pace-track', 'aria-hidden': 'true' },
		                        h('span', { className: 'pace-fill', style: { width: `${percent}%` } })
		                      ),
		                      h('span', { className: 'pace-value' }, paceText),
		                      h('span', { className: `pace-change ${paceChangeState}` }, paceChangeText)
		                    );
		                  }

		                  function pacePercent(score) {
		                    const value = Number(score || 0);
		                    if (!Number.isFinite(value)) return 0;
		                    return Math.max(0, Math.min(100, Math.round(value)));
		                  }

		                  function sandwichRankBadge(sandwichCell) {
		                    const rank = Number(sandwichCell?.rank);
		                    if (!Number.isFinite(rank)) return null;
		                    return h('sup', {
		                      className: 'sandwich-rank-badge',
		                      'aria-label': `Volume sandwich rank ${rank}`,
		                      title: `Volume sandwich rank ${rank}`
		                    }, rank);
		                  }

		                  function sandwichStrikeCells(currentTop2) {
		                    const cells = new Map();
		                    currentTop2
		                      .filter(sandwich => sandwich && sandwich.active !== false && Number(sandwich.rank) <= 2)
		                      .forEach(sandwich => {
		                        const rank = Number(sandwich.rank);
		                        const side = String(sandwich.side || '').toLowerCase();
		                        if (side !== 'call' && side !== 'put') return;
		                        const apply = (strike, role) => {
		                          const key = sandwichCellKey(strike, side);
		                          const existing = cells.get(key);
		                          if (existing && existing.rank <= rank) return;
		                          cells.set(key, {
		                            rank,
		                            className: `sandwich-cell sandwich-side-${side} sandwich-rank-${rank} sandwich-role-${role}`,
		                            title: `${side.toUpperCase()} volume sandwich #${rank} ${role.toUpperCase()} ${sandwich.lowerStrike}/${sandwich.midStrike}/${sandwich.upperStrike} strength ${fmtStrength(sandwich.currentStrength)}x`
		                          });
		                        };
		                        const lowerVolume = Number(sandwich.lowerVolume || 0);
		                        const upperVolume = Number(sandwich.upperVolume || 0);
		                        const lowerRole = lowerVolume >= upperVolume ? 'top' : 'outer';
		                        const upperRole = upperVolume > lowerVolume ? 'top' : 'outer';
		                        apply(sandwich.lowerStrike, lowerRole);
		                        apply(sandwich.midStrike, 'middle');
		                        apply(sandwich.upperStrike, upperRole);
		                      });
		                    return cells;
		                  }

	                  function sandwichCellKey(strike, side) {
	                    return `${strikeKey(strike)}:${side}`;
	                  }

	                  function strikeKey(strike) {
	                    const value = Number(strike);
	                    if (!Number.isFinite(value)) return String(strike);
	                    return Number.isInteger(value) ? String(value) : String(value);
	                  }

                  function contractKey(payload, fallbackConfig = {}) {
                    const source = sourceKey(payload, fallbackConfig);
                    const symbol = String(payload?.symbol || fallbackConfig?.symbol || '').toUpperCase();
                    const expiry = normalizeExpiry(payload?.expiry || fallbackConfig?.expiry);
                    return `${source}|${symbol}|${expiry}|${strikeKey(payload?.strike)}`;
                  }

                  function activeContractKey(payload, config) {
                    return contractKey(payload, config);
                  }

                  function sourceKey(payload, fallbackConfig = {}) {
                    return String(payload?.source || payload?.marketDataSource || fallbackConfig?.marketDataSource || 'LEGACY').toUpperCase();
                  }

                  function matchingStrikeKeys(maps, payload) {
                    const strike = strikeKey(payload?.strike);
                    const keys = new Set();
                    maps.forEach(map => {
                      [...map.keys()].forEach(key => {
                        if (String(key).split('|').pop() === strike || strikeKey(map.get(key)?.strike) === strike) {
                          keys.add(key);
                        }
                      });
                    });
                    return [...keys];
                  }

                  // Reset-aware running total for a backend counter that should only ever grow within a
                  // session. Goal (per requirement): the displayed cumulative $ must keep increasing/decreasing
                  // but NEVER snap back to 0 just because a window/session aggregate was reset upstream or a
                  // transient empty payload arrived.
                  //   - incoming <= 0 (empty/quiet update): keep the prior cumulative AND the prior raw
                  //     baseline (so a spurious 0 cannot zero the display or corrupt the delta math).
                  //   - incoming >= baseline: normal forward progress — add the delta.
                  //   - incoming <  baseline: upstream counter reset — treat the whole incoming as fresh flow
                  //     and add it on top (the running total never drops to 0).
                  function accumulateNotional(prevCum, prevRaw, incoming) {
                    const raw = Number(incoming || 0);
                    const cum = Number(prevCum || 0);
                    const base = Number(prevRaw || 0);
                    if (!(raw > 0)) return { cum, raw: base };
                    const delta = raw >= base ? raw - base : raw;
                    return { cum: cum + delta, raw };
                  }

                  function normalizeStrikeFlowPayload(market, strikePayload, prevFlow) {
                    const classification = String(strikePayload?.classification || 'MIXED').toUpperCase();
                    const displayColor = String(strikePayload?.displayColor || strikeFlowDisplayColor(classification)).toUpperCase();
                    const strike = Number(strikePayload?.strike);
                    // Carry the cumulative $ forward across resets so the superscript is sticky (never 0).
                    const callBuy = accumulateNotional(prevFlow?.callBuyNotional, prevFlow?.callBuyNotionalRaw, strikePayload?.callBuyNotional);
                    const callSell = accumulateNotional(prevFlow?.callSellNotional, prevFlow?.callSellNotionalRaw, strikePayload?.callSellNotional);
                    const putBuy = accumulateNotional(prevFlow?.putBuyNotional, prevFlow?.putBuyNotionalRaw, strikePayload?.putBuyNotional);
                    const putSell = accumulateNotional(prevFlow?.putSellNotional, prevFlow?.putSellNotionalRaw, strikePayload?.putSellNotional);
                    return {
                      strikeFlow: {
                        market: {
                          symbol: String(market?.symbol || '').toUpperCase(),
                          expiry: normalizeExpiry(market?.expiry),
                          source: sourceKey(market),
                          timestampMs: Number(market?.timestampMs || 0)
                        },
                        strike,
                        classification,
                        displayColor,
                        superscriptNotional: String(strikePayload?.superscriptNotional || ''),
                        blink: Boolean(strikePayload?.blink),
                        blinkMode: String(strikePayload?.blinkMode || '').toUpperCase(),
                        topBuyRank: Number(strikePayload?.topBuyRank || 0),
                        topSellRank: Number(strikePayload?.topSellRank || 0),
                        buyBias: Number(strikePayload?.buyBias || 0),
                        sellBias: Number(strikePayload?.sellBias || 0),
                        // Displayed value = session cumulative (sticky). *Raw = last non-zero upstream value,
                        // kept only as the baseline for reset detection on the next update.
                        callBuyNotional: callBuy.cum,
                        callSellNotional: callSell.cum,
                        putBuyNotional: putBuy.cum,
                        putSellNotional: putSell.cum,
                        callBuyNotionalRaw: callBuy.raw,
                        callSellNotionalRaw: callSell.raw,
                        putBuyNotionalRaw: putBuy.raw,
                        putSellNotionalRaw: putSell.raw,
                        callBuyVolume: Number(strikePayload?.callBuyVolume || 0),
                        callSellVolume: Number(strikePayload?.callSellVolume || 0),
                        putBuyVolume: Number(strikePayload?.putBuyVolume || 0),
                        putSellVolume: Number(strikePayload?.putSellVolume || 0),
                        lastUpdatedMs: Number(strikePayload?.lastUpdatedMs || 0)
                      }
                    };
                  }

                  function strikeFlowDisplayColor(classification) {
                    switch (String(classification || '').toUpperCase()) {
                      case 'BUY_BIAS':
                        return 'GREEN';
                      case 'SELL_BIAS':
                        return 'RED';
                      default:
                        return 'NEUTRAL';
                    }
                  }

                  function strikeFlowLeaderboard(rows) {
                    const entries = (rows || [])
                      .map(row => row?.strikeFlow)
                      .filter(flow => flow && Number.isFinite(Number(flow.strike)));
                    const buy = entries
                      .filter(flow => Number(flow.topBuyRank) > 0 || Number(flow.buyBias) > 0)
                      .sort((a, b) => strikeFlowRankSort(a, b, 'buy'))
                      .slice(0, 3)
                      .map((flow, index) => strikeFlowLeaderRow(flow, 'buy', index + 1));
                    const sell = entries
                      .filter(flow => Number(flow.topSellRank) > 0 || Number(flow.sellBias) > 0)
                      .sort((a, b) => strikeFlowRankSort(a, b, 'sell'))
                      .slice(0, 3)
                      .map((flow, index) => strikeFlowLeaderRow(flow, 'sell', index + 1));
                    return { buy, sell };
                  }

                  function strikeFlowRankSort(left, right, side) {
                    const rankField = side === 'buy' ? 'topBuyRank' : 'topSellRank';
                    const biasField = side === 'buy' ? 'buyBias' : 'sellBias';
                    const leftRank = positiveNumber(left?.[rankField]);
                    const rightRank = positiveNumber(right?.[rankField]);
                    if (leftRank > 0 || rightRank > 0) {
                      if (leftRank <= 0) return 1;
                      if (rightRank <= 0) return -1;
                      if (leftRank !== rightRank) return leftRank - rightRank;
                    }
                    return positiveNumber(right?.[biasField]) - positiveNumber(left?.[biasField]);
                  }

                  function strikeFlowLeaderRow(flow, side, fallbackRank) {
                    const rank = side === 'buy'
                      ? Number(flow.topBuyRank || fallbackRank)
                      : Number(flow.topSellRank || fallbackRank);
                    const notionalValue = side === 'buy' ? flow.buyBias : flow.sellBias;
                    const callNotional = side === 'buy' ? flow.callBuyNotional : flow.callSellNotional;
                    const putNotional = side === 'buy' ? flow.putSellNotional : flow.putBuyNotional;
                    const dominantSide = positiveNumber(callNotional) >= positiveNumber(putNotional) ? 'call' : 'put';
                    const context = side === 'buy'
                      ? `${dominantSide.toUpperCase()} accumulation / support`
                      : `${dominantSide.toUpperCase()} pressure / resistance`;
                    return {
                      rank,
                      strike: strikeKey(flow.strike),
                      notional: fmtStrikeFlowUsd(notionalValue),
                      context,
                      title: [
                        `${side.toUpperCase()} flow #${rank}`,
                        `strike ${strikeKey(flow.strike)}`,
                        `notional ${fmtStrikeFlowUsd(notionalValue)}`,
                        `calls ${fmtStrikeFlowUsd(callNotional)}`,
                        `puts ${fmtStrikeFlowUsd(putNotional)}`
                      ].join(' | ')
                    };
                  }

                  function strikeFlowVolumeState(row, side) {
                    const flow = row?.strikeFlow;
                    if (!flow) return undefined;
                    const isCall = side === 'call';
                    const isPut = side === 'put';
                    if (!isCall && !isPut) return undefined;
                    // SIGNED CUMULATIVE NET — the displayed superscript is ONE signed $ per cell that drifts up/down
                    // through the session (callBuy/callSell/... are already session-cumulative via accumulateNotional).
                    // Sign follows the bullish/bearish convention so green/red stays consistent with the chain:
                    //   call net = callBuy - callSell  (+ = net call BUYING = bullish, - = net call selling = bearish)
                    //   put  net = putSell  - putBuy   (+ = net put SELLING/support = bullish, - = net put buying/protection)
                    const buy = Number((isCall ? flow.callBuyNotional : flow.putBuyNotional) || 0);
                    const sell = Number((isCall ? flow.callSellNotional : flow.putSellNotional) || 0);
                    const buyVolume = Number((isCall ? flow.callBuyVolume : flow.putBuyVolume) || 0);
                    const sellVolume = Number((isCall ? flow.callSellVolume : flow.putSellVolume) || 0);
                    const net = isCall ? (buy - sell) : (sell - buy);
                    const bullish = net > 0;
                    const bearish = net < 0;
                    const className = bullish ? 'strike-flow-buy' : (bearish ? 'strike-flow-sell' : 'strike-flow-mixed');
                    const sign = bullish ? '+' : (bearish ? '-' : '');
                    // Exactly-zero net (no flow / perfectly balanced) renders NO superscript — no more "$0" clutter on
                    // the many strikes that have no net flow. Non-zero strikes show e.g. "+$1.2M" (green) / "-$800K" (red).
                    const superscriptNotional = net === 0 ? '' : `${sign}${fmtStrikeFlowUsd(Math.abs(net))}`;
                    const pulse = Boolean(flow.blink) && net !== 0
                      && (Number(flow.topBuyRank || 0) > 0 || Number(flow.topSellRank || 0) > 0);
                    const label = isCall
                      ? (bullish ? 'Call net buying' : bearish ? 'Call net selling' : 'Call balanced')
                      : (bullish ? 'Put net selling / support' : bearish ? 'Put net buying / protection' : 'Put balanced');
                    const title = `${label} | net ${sign}${fmtStrikeFlowUsd(Math.abs(net))}`
                      + ` | buy ${fmtStrikeFlowUsd(buy)} | sell ${fmtStrikeFlowUsd(sell)}`
                      + ` | buy vol ${fmtVolume(buyVolume)} | sell vol ${fmtVolume(sellVolume)}`;
                    return { className, pulse, superscriptNotional, title };
                  }

                  function strikeFlowRowClass(row) {
                    const flow = row?.strikeFlow;
                    if (!flow) return '';
                    const color = String(flow.displayColor || strikeFlowDisplayColor(flow.classification)).toUpperCase();
                    if (color === 'GREEN') return 'strike-flow-row-buy';
                    if (color === 'RED') return 'strike-flow-row-sell';
                    return 'strike-flow-row-mixed';
                  }

	                  function normalizeConfig(config) {
    return {
      appProfile: String(config?.appProfile || defaultConfig.appProfile),
      apiBaseUrl: String(config?.apiBaseUrl || defaultConfig.apiBaseUrl),
      missionControlUrl: String(config?.missionControlUrl || defaultConfig.missionControlUrl),
      provider: String(config?.provider || defaultConfig.provider).toUpperCase(),
      marketDataSource: String(config?.marketDataSource || defaultConfig.marketDataSource).toUpperCase(),
      symbol: String(config?.symbol || defaultConfig.symbol).toUpperCase(),
      expiry: String(config?.expiry || defaultConfig.expiry),
      port: config?.port ?? defaultConfig.port,
      clientId: config?.clientId ?? defaultConfig.clientId,
      maxStrikes: config?.maxStrikes ?? defaultConfig.maxStrikes,
      delayed: Boolean(config?.delayed),
      feedGatewayEnabled: config?.feedGatewayEnabled ?? defaultConfig.feedGatewayEnabled,
      feedGatewayAvailable: config?.feedGatewayAvailable ?? defaultConfig.feedGatewayAvailable,
      feedGatewayWsUrl: String(config?.feedGatewayWsUrl || defaultConfig.feedGatewayWsUrl || defaultWebSocketUrl()),
      databentoReplayUiEnabled: Boolean(config?.databentoReplayUiEnabled ?? defaultConfig.databentoReplayUiEnabled)
    };
  }

  function isDevProfile(config) {
    return String(config?.appProfile || '').toLowerCase() === 'dev';
  }

  function isDatabentoMarketData(config) {
    return String(config?.marketDataSource || '').toUpperCase() === 'DATABENTO';
  }

  function selectionConfig(currentConfig, payload) {
    return {
      ...(currentConfig || defaultConfig),
      marketDataSource: String(payload?.marketDataSource || currentConfig?.marketDataSource || defaultConfig.marketDataSource).toUpperCase(),
      symbol: String(payload?.symbol || currentConfig?.symbol || defaultConfig.symbol).toUpperCase(),
      expiry: normalizeExpiry(payload?.expiry || currentConfig?.expiry || defaultConfig.expiry)
    };
  }

  function feedUnavailableNotice(config) {
    return `Feed is unavailable. Feed gateway is not reachable at ${gatewayWebSocketUrl(config)}.`;
  }

  function gatewayWebSocketUrl(config) {
    return normalizeWebSocketUrl(config?.feedGatewayWsUrl || defaultConfig.feedGatewayWsUrl || defaultWebSocketUrl());
  }

  function normalizeWebSocketUrl(value) {
    const url = String(value || '').trim();
    if (url.startsWith('ws://') || url.startsWith('wss://')) return browserReachableGatewayUrl(url);
    if (url.startsWith('http://')) return browserReachableGatewayUrl(`ws://${url.slice('http://'.length)}`);
    if (url.startsWith('https://')) return browserReachableGatewayUrl(`wss://${url.slice('https://'.length)}`);
    return defaultWebSocketUrl();
  }

  function browserReachableGatewayUrl(value) {
    try {
      const url = new URL(value);
      const pageHost = window.location.hostname;
      if (isBrowserLocalGatewayHost(url.hostname) && pageHost && !isBrowserLocalGatewayHost(pageHost)) {
        url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        url.hostname = pageHost;
        url.port = window.location.port || '';
      }
      return url.toString();
    } catch (error) {
      return defaultWebSocketUrl();
    }
  }

  function apiUrl(path) {
    const base = String(runtimeEnv.VITE_API_BASE_URL || defaultConfig.apiBaseUrl || '').trim();
    if (!base) return path;
    return `${base.replace(/\/+$/, '')}/${String(path || '').replace(/^\/+/, '')}`;
  }

  function defaultWebSocketUrl() {
    if (!window.location) return '';
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/events`;
  }

  function isBrowserLocalGatewayHost(hostname) {
    const host = String(hostname || '').toLowerCase();
    return isLoopbackHost(host) || isPrivateIpv4Host(host);
  }

  function isLoopbackHost(hostname) {
    const host = String(hostname || '').toLowerCase();
    return host === '127.0.0.1' || host === 'localhost' || host === '::1' || host === '[::1]';
  }

  function isPrivateIpv4Host(hostname) {
    const parts = String(hostname || '').split('.');
    if (parts.length !== 4) return false;
    const octets = parts.map(part => Number(part));
    if (octets.some(octet => !Number.isInteger(octet) || octet < 0 || octet > 255)) return false;
    const [first, second] = octets;
    return first === 10
      || (first === 172 && second >= 16 && second <= 31)
      || (first === 192 && second === 168)
      || (first === 169 && second === 254);
  }

  function matchesActiveConfig(payload, config) {
    const activeSymbol = String(config?.symbol || '').toUpperCase();
    const activeExpiry = normalizeExpiry(config?.expiry);
    if (!activeSymbol || !activeExpiry) return false;
    return String(payload?.symbol || '').toUpperCase() === activeSymbol
      && normalizeExpiry(payload?.expiry) === activeExpiry;
  }

	  function shouldAcceptStreamPayload(payload, config) {
	    return matchesActiveConfig(payload, config) && matchesActiveSource(payload, config);
	  }

  function shouldAcceptStrikeFlowPayload(payload, config) {
    if (!matchesActiveConfig(payload, config)) return false;
    const payloadSource = String(payload?.marketDataSource || payload?.source || '').toUpperCase();
    if (payloadSource) return matchesActiveSource(payload, config);
    return String(config?.marketDataSource || '').toUpperCase() === 'DATABENTO';
  }

  function matchesActiveSource(payload, config) {
    const activeSource = String(config?.marketDataSource || '').toUpperCase();
    if (!activeSource) return true;
    const payloadSource = String(payload?.marketDataSource || payload?.source || '').toUpperCase();
    return payloadSource === activeSource;
  }

  function batchItems(payload, field) {
    const value = payload?.[field];
    if (Array.isArray(value)) return value;
    return value ? [value] : [];
  }

	  function lastAcceptedPayload(items, config) {
	    let latest;
	    items.forEach(item => {
	      if (shouldAcceptStreamPayload(item, config)) latest = item;
	    });
	    return latest;
	  }

	  function lastIndexPricePayload(items, config, symbol) {
	    let latest;
	    items.forEach(item => {
	      if (shouldAcceptIndexPricePayload(item, config, symbol)) latest = item;
	    });
	    return latest;
	  }

	  function shouldAcceptIndexPricePayload(payload, config, symbol) {
	    const activeSource = String(config?.marketDataSource || '').toUpperCase();
	    const symbols = Array.isArray(symbol) ? symbol : [symbol];
	    const acceptedSymbols = symbols.map(item => String(item || '').toUpperCase());
	    const payloadSymbol = String(payload?.displaySymbol || payload?.rawSymbol || payload?.symbol || '').toUpperCase();
	    const acceptsVix = acceptedSymbols.includes('VIX');
	    const acceptsEs = acceptedSymbols.includes('ESM6') || acceptedSymbols.includes('ES.V.0');
	    if (acceptsVix) return acceptedSymbols.includes(payloadSymbol);
	    if (!matchesActiveSource(payload, config)) return false;
	    if (acceptsEs && activeSource && activeSource !== 'DATABENTO') return false;
	    return acceptedSymbols.includes(payloadSymbol);
	  }

  function lastPayload(items) {
    return items.length ? items[items.length - 1] : undefined;
  }

  function hpsfFallbackSignal(status) {
    const stale = String(status?.mode || '').toLowerCase() === 'stale' || String(status?.text || '').toLowerCase().includes('unavailable');
    return {
      action: 'NO_TRADE',
      displayState: stale ? 'DATA_STALE' : 'NO_TRADE',
      title: stale ? 'DATA STALE - NO TRADE' : 'NO TRADE',
      executionText: '--',
      confidence: 0,
      setup: 'WAITING_FOR_HPSF',
      reasons: ['Waiting for gateway options.hpsf.latest-signal view'],
      riskText: stale ? 'Data not safe until gateway sources recover' : 'No HPSF signal received yet',
      colorClass: stale ? 'no-trade' : 'no-trade'
    };
  }

  function hpsfSignalKey(signal) {
    if (!signal) return '';
    return [
      signal.evaluationId,
      signal.eventTime,
      signal.displayState,
      signal.action,
      signal.executionText,
      signal.executionStrike,
      signal.confidence
    ].filter(value => value !== undefined && value !== null && value !== '').join('|');
  }

  function hpsfDisplayState(signal) {
    const state = String(signal?.displayState || signal?.action || 'NO_TRADE').toUpperCase();
    if (state.includes('DATA_STALE') || hpsfReasons(signal).some(reason => /STALE|LAG|DATA NOT SAFE|BAD DATA/i.test(reason))) return 'DATA_STALE';
    if (state.includes('MIXED_FLOW') || hpsfReasons(signal).some(reason => /MIXED FLOW/i.test(reason))) return 'MIXED_FLOW';
    return state;
  }

  function hpsfActionSpec(displayState) {
    switch (displayState) {
      case 'BUY_CALL_CONFIRMED':
        return { label: 'BUY CALL CONFIRMED', colorClass: 'buy-call-confirmed', icon: '✅' };
      case 'BUY_CALL_EARLY':
        return { label: 'BUY CALL EARLY', colorClass: 'buy-call-early', icon: '⚡' };
      case 'WATCH_CALL_RECLAIM':
        return { label: 'WATCH CALL RECLAIM', colorClass: 'watch-call', icon: '👀' };
      case 'BUY_PUT_CONFIRMED':
        return { label: 'BUY PUT CONFIRMED', colorClass: 'buy-put-confirmed', icon: '✅' };
      case 'BUY_PUT_EARLY':
        return { label: 'BUY PUT EARLY', colorClass: 'buy-put-early', icon: '⚡' };
      case 'WATCH_PUT_BREAKDOWN':
        return { label: 'WATCH PUT BREAKDOWN', colorClass: 'watch-put', icon: '👀' };
      case 'MIXED_FLOW':
        return { label: 'MIXED FLOW - NO TRADE', colorClass: 'no-trade mixed-flow', icon: '⚠' };
      case 'DATA_STALE':
        return { label: 'DATA STALE - NO TRADE', colorClass: 'no-trade data-stale', icon: '⛔' };
      case 'NO_TRADE':
      default:
        return { label: 'NO TRADE', colorClass: 'no-trade', icon: '—' };
    }
  }

  function hpsfExecutionText(signal) {
    if (signal?.executionText) return signal.executionText;
    if (signal?.executionStrike && signal?.selectedOptionType) return `${signal.executionStrike} ${signal.selectedOptionType}`;
    return '--';
  }

  function hpsfReasons(signal) {
    if (Array.isArray(signal?.reasons)) return signal.reasons;
    if (signal?.reason) return [signal.reason];
    if (signal?.reasonSummary) return [signal.reasonSummary];
    return [];
  }

  function hpsfText(value) {
    return String(value || '--').replaceAll('_', ' ');
  }

  function firstValue(values) {
    return Array.isArray(values) && values.length ? values[0] : undefined;
  }

  function fmtHpsfNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number.toFixed(2) : '--';
  }

  function fmtSignedHpsfNumber(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '--';
    return `${number > 0 ? '+' : ''}${number.toFixed(2)}`;
  }

  function fmtHpsfScore(value) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.round(number).toString() : '--';
  }

  function fmtHpsfUsd(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '--';
    const sign = number > 0 ? '+' : number < 0 ? '-' : '';
    const abs = Math.abs(number);
    if (abs >= 1_000_000) return `${sign}$${stripTrailingZero((abs / 1_000_000).toFixed(1))}M`;
    if (abs >= 1_000) return `${sign}$${stripTrailingZero((abs / 1_000).toFixed(1))}k`;
    return `${sign}$${stripTrailingZero(abs.toFixed(0))}`;
  }

  function fmtStrikeFlowUsd(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '--';
    const abs = Math.abs(number);
    if (abs >= 1_000_000) return `$${stripTrailingZero((number / 1_000_000).toFixed(1))}M`;
    if (abs >= 1_000) return `$${stripTrailingZero((number / 1_000).toFixed(1))}K`;
    return `$${stripTrailingZero(number.toFixed(0))}`;
  }

  function fmtHpsfPct(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '--';
    const pct = Math.abs(number) <= 1 ? number * 100 : number;
    return `${stripTrailingZero(pct.toFixed(1))}%`;
  }

  function fmtHpsfMultiple(value) {
    const number = Number(value);
    return Number.isFinite(number) ? `${stripTrailingZero(number.toFixed(1))}x` : '--';
  }

  function clampPercent(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return 0;
    return Math.max(0, Math.min(100, Math.round(number)));
  }

  function hpsfHealthClass(value) {
    const text = String(value || '').toUpperCase();
    if (text.includes('STALE') || text.includes('DOWN') || text.includes('BAD')) return 'danger';
    if (text.includes('WARM') || text.includes('CHECK') || text.includes('DELAY')) return 'warn';
    return 'ok';
  }

  function hpsfOptionSideClass(optionType) {
    const type = String(optionType || '').toUpperCase();
    if (type === 'PUT') return 'put-side';
    if (type === 'CALL') return 'call-side';
    return '';
  }

  function hpsfFindCandidate(topCandidates, strike, optionType, roleHint) {
    const targetStrike = Number(strike);
    if (!Number.isFinite(targetStrike)) return undefined;
    const side = String(optionType || '').toUpperCase();
    const rows = [
      ...hpsfCandidateRows(topCandidates, side || 'CALL'),
      ...hpsfCandidateRows(topCandidates, side || 'PUT')
    ];
    const role = String(roleHint || '').toUpperCase();
    return rows.find(row => Number(row.strike) === targetStrike && (!role || String(row.candidateRole || '').toUpperCase().includes(role)))
      || rows.find(row => Number(row.strike) === targetStrike);
  }

  function hpsfCandidateRows(topCandidates, side) {
    if (!topCandidates) return [];
    const upper = String(side || '').toUpperCase();
    const execution = upper === 'PUT' ? topCandidates.topPutExecutionCandidates : topCandidates.topCallExecutionCandidates;
    const anchors = upper === 'PUT' ? topCandidates.putFlowAnchors : topCandidates.callFlowAnchors;
    return [...(execution || []), ...(anchors || [])];
  }

  function hpsfCandidateClass(row) {
    const role = String(row?.candidateRole || '').toUpperCase();
    const classes = [];
    if (role.includes('EXEC')) classes.push('hpsf-candidate-execution');
    if (role.includes('ANCHOR')) classes.push('hpsf-candidate-anchor');
    if (row?.liquidityOk === false || row?.candidateDistanceOk === false) classes.push('hpsf-candidate-rejected');
    return classes.join(' ');
  }

  function hpsfCandidateOk(row) {
    if (row?.liquidityOk === false) return 'Liquidity';
    if (row?.candidateDistanceOk === false) return 'Distance';
    if (row?.reason) return row.reason;
    return 'OK';
  }

  function hpsfVwapPath(signal, marketFlow) {
    const state = String(marketFlow?.internalVwapState || signal?.internalVwapState || signal?.setup || '').toUpperCase();
    const display = hpsfDisplayState(signal);
    const bearish = display.includes('PUT') || state.includes('ABOVE') || state.includes('BREAK');
    const hardFailed = state.includes('HARD') || display === 'NO_TRADE' && hpsfReasons(signal).some(reason => /HARD|VWAP/i.test(reason));
    const labels = bearish
      ? ['Above VWAP', 'First Test', 'Soft Bounce', 'Lower High', 'Second Approach', 'Confirmed Below VWAP']
      : ['Below VWAP', 'First Test', 'Soft Reject', 'Higher Low', 'Second Approach', 'Confirmed Above VWAP'];
    const activeIndex = hpsfVwapActiveIndex(state, display);
    return {
      title: bearish ? 'VWAP Breakdown Path' : 'VWAP Reclaim Path',
      steps: labels.map((label, index) => {
        const failed = hardFailed && index >= Math.min(activeIndex + 1, labels.length - 1);
        const done = index < activeIndex || display.includes('CONFIRMED');
        const current = index === activeIndex && !display.includes('CONFIRMED') && !hardFailed;
        return {
          label,
          state: failed ? 'failed' : done ? 'done' : current ? 'current' : 'pending',
          icon: failed ? '×' : done ? '✓' : current ? '●' : '○',
          detail: index === 4 && signal?.eventTime ? formatTime(signal.eventTime) : ''
        };
      })
    };
  }

  function hpsfVwapActiveIndex(state, display) {
    if (display.includes('CONFIRMED') || state.includes('CONFIRMED')) return 5;
    if (state.includes('SECOND_APPROACH') || display.includes('EARLY')) return 4;
    if (state.includes('HIGHER_LOW') || state.includes('LOWER_HIGH')) return 3;
    if (state.includes('SOFT')) return 2;
    if (state.includes('TEST') || display.includes('WATCH')) return 1;
    return 0;
  }

  function hpsfRejectedText(item) {
    if (typeof item === 'string') return hpsfText(item);
    return hpsfText(item?.action || item?.name || '--') + (item?.reason ? `: ${hpsfText(item.reason)}` : '');
  }

	  function normalizeExpiry(expiry) {
	    return String(expiry || '').replaceAll('-', '');
	  }

  function formatExpiry(expiry) {
    if (!expiry || expiry.length !== 8) return expiry || '--';
    return `${expiry.slice(0, 4)}-${expiry.slice(4, 6)}-${expiry.slice(6, 8)}`;
  }

  function formatExpiryDisplay(expiry) {
    const normalized = normalizeExpiry(expiry);
    if (!normalized || normalized.length !== 8) return normalized || '--';
    return `${normalized.slice(6, 8)}-${normalized.slice(4, 6)}-${normalized.slice(0, 4)}`;
  }

  function toDateInput(expiry) {
    const formatted = formatExpiry(expiry);
    return formatted === '--' ? '' : formatted;
  }

  // Today's exchange-local (ET) date as an ISO "YYYY-MM-DD" string for date-input min/max bounds, or ''
  // when the calendar can't resolve it. A LIVE expiry must be today or a future trading day — a past
  // expiry has no live data — so the Expiry picker uses this as its min.
  function marketTodayIso() {
    const t = MarketCalendar.marketDateTimeParts(new Date());
    return t ? `${t.year}-${t.month}-${t.day}` : '';
  }

  function toIbExpiry(value) {
    return String(value || '').replaceAll('-', '');
  }

  function nextWeekdayExpiry(expiry) {
    const normalized = normalizeExpiry(expiry);
    if (!/^\d{8}$/.test(normalized)) return normalized;
    const date = new Date(`${normalized.slice(0, 4)}-${normalized.slice(4, 6)}-${normalized.slice(6, 8)}T00:00:00Z`);
    if (Number.isNaN(date.getTime())) return normalized;
    while (!isMarketDate(date)) {
      date.setUTCDate(date.getUTCDate() + 1);
    }
    return utcExpiry(date);
  }

  function isMarketDate(date) {
    return MarketCalendar.isTradingDay(utcExpiry(date));
  }

  function isMarketExpiry(expiry) {
    const normalized = normalizeExpiry(expiry);
    if (!/^\d{8}$/.test(normalized)) return false;
    const date = new Date(`${normalized.slice(0, 4)}-${normalized.slice(4, 6)}-${normalized.slice(6, 8)}T00:00:00Z`);
    return !Number.isNaN(date.getTime()) && isMarketDate(date);
  }

  function utcExpiry(date) {
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const day = String(date.getUTCDate()).padStart(2, '0');
    return `${year}${month}${day}`;
  }

  function marketDateTimeParts(now) {
    return MarketCalendar.marketDateTimeParts(now);
  }

  function createMarketCalendar() {
    const EXCHANGE_TIME_ZONE = 'America/New_York';
    const FULL_CLOSE_MINUTE = 16 * 60 + 15;
    const EARLY_CLOSE_MINUTE = 13 * 60;

    return {
      previousTradingDay,
      isTradingDay,
      isWeekend,
      isFullMarketHoliday,
      isEarlyClose,
      observedDateForHoliday,
      marketDateTimeParts,
      exchangeLocalToUtcInstant,
      fullCloseMinute: FULL_CLOSE_MINUTE,
      earlyCloseMinute: EARLY_CLOSE_MINUTE
    };

    function previousTradingDay(now = new Date()) {
      const today = marketDateFromInstant(now);
      let candidate = addDays(today, -1);
      while (!isTradingDay(candidate)) {
        candidate = addDays(candidate, -1);
      }
      return candidate;
    }

    function isTradingDay(date) {
      const marketDate = normalizeMarketDate(date);
      return Boolean(marketDate) && !isWeekend(marketDate) && !isFullMarketHoliday(marketDate);
    }

    function isWeekend(date) {
      const marketDate = normalizeMarketDate(date);
      if (!marketDate) return false;
      const day = dateToUtcNoon(marketDate).getUTCDay();
      return day === 0 || day === 6;
    }

    function isFullMarketHoliday(date) {
      const marketDate = normalizeMarketDate(date);
      if (!marketDate) return false;
      const holidays = fullMarketHolidays(marketDate.year);
      return holidays.has(marketDate.key);
    }

    function isEarlyClose(date) {
      const marketDate = normalizeMarketDate(date);
      if (!marketDate || !isTradingDay(marketDate)) return false;
      return isDayBeforeIndependenceDay(marketDate)
        || marketDate.key === addDays(thanksgiving(marketDate.year), 1).key
        || isChristmasEve(marketDate);
    }

    function observedDateForHoliday(date) {
      const marketDate = normalizeMarketDate(date);
      if (!marketDate) return undefined;
      const weekday = dateToUtcNoon(marketDate).getUTCDay();
      if (weekday === 6) return addDays(marketDate, -1);
      if (weekday === 0) return addDays(marketDate, 1);
      return marketDate;
    }

    function exchangeLocalToUtcInstant(value) {
      const parts = requireExchangeLocalDateTime(value, 'Replay date/time');
      const matches = matchingUtcInstantsForLocal(parts);
      if (matches.length === 0) {
        throw new Error('Replay date/time does not exist in America/New_York.');
      }
      if (matches.length > 1) {
        throw new Error('Replay date/time is ambiguous in America/New_York.');
      }
      return new Date(matches[0]).toISOString().replace(/\.\d{3}Z$/, 'Z');
    }

    function matchingUtcInstantsForLocal(parts) {
      let utcMillis = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, 0, 0);
      const targetLocalMillis = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, 0, 0);
      for (let index = 0; index < 3; index += 1) {
        const actual = marketDateTimeParts(new Date(utcMillis));
        if (!actual) break;
        const actualLocalMillis = Date.UTC(Number(actual.year), Number(actual.month) - 1, Number(actual.day), actual.hour, actual.minute, 0, 0);
        const delta = targetLocalMillis - actualLocalMillis;
        if (delta === 0) break;
        utcMillis += delta;
      }
      const matches = [];
      const start = utcMillis - 3 * 60 * 60 * 1000;
      const end = utcMillis + 3 * 60 * 60 * 1000;
      for (let candidate = start; candidate <= end; candidate += 60 * 1000) {
        const actual = marketDateTimeParts(new Date(candidate));
        if (actual
            && Number(actual.year) === parts.year
            && Number(actual.month) === parts.month
            && Number(actual.day) === parts.day
            && actual.hour === parts.hour
            && actual.minute === parts.minute) {
          matches.push(candidate);
        }
      }
      return matches;
    }

    function marketDateFromInstant(now) {
      const parts = marketDateTimeParts(now);
      if (!parts) {
        const fallback = new Date(now);
        return marketDate(fallback.getUTCFullYear(), fallback.getUTCMonth() + 1, fallback.getUTCDate());
      }
      return marketDate(Number(parts.year), Number(parts.month), Number(parts.day));
    }

    function marketDateTimeParts(now) {
      try {
        const parts = new Intl.DateTimeFormat('en-US', {
          timeZone: EXCHANGE_TIME_ZONE,
          weekday: 'short',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hour12: false
        }).formatToParts(now).reduce((values, part) => ({ ...values, [part.type]: part.value }), {});
        return {
          weekday: parts.weekday,
          year: parts.year,
          month: parts.month,
          day: parts.day,
          hour: Number(parts.hour === '24' ? '0' : parts.hour),
          minute: Number(parts.minute)
        };
      } catch (error) {
        return undefined;
      }
    }

    function fullMarketHolidays(year) {
      const holidays = new Set();
      [year - 1, year, year + 1].forEach(holidayYear => {
        [
          observedDateForHoliday(marketDate(holidayYear, 1, 1)),
          nthWeekdayOfMonth(holidayYear, 1, 1, 3),
          nthWeekdayOfMonth(holidayYear, 2, 1, 3),
          addDays(easterSunday(holidayYear), -2),
          lastWeekdayOfMonth(holidayYear, 5, 1),
          observedDateForHoliday(marketDate(holidayYear, 6, 19)),
          observedDateForHoliday(marketDate(holidayYear, 7, 4)),
          nthWeekdayOfMonth(holidayYear, 9, 1, 1),
          thanksgiving(holidayYear),
          observedDateForHoliday(marketDate(holidayYear, 12, 25))
        ].filter(Boolean).forEach(holiday => holidays.add(holiday.key));
      });
      return holidays;
    }

    function isDayBeforeIndependenceDay(date) {
      return date.month === 7 && date.day === 3;
    }

    function isChristmasEve(date) {
      return date.month === 12 && date.day === 24;
    }

    function thanksgiving(year) {
      return nthWeekdayOfMonth(year, 11, 4, 4);
    }

    function nthWeekdayOfMonth(year, month, weekday, occurrence) {
      let date = marketDate(year, month, 1);
      while (dateToUtcNoon(date).getUTCDay() !== weekday) {
        date = addDays(date, 1);
      }
      return addDays(date, (occurrence - 1) * 7);
    }

    function lastWeekdayOfMonth(year, month, weekday) {
      let date = marketDate(year, month + 1, 0);
      while (dateToUtcNoon(date).getUTCDay() !== weekday) {
        date = addDays(date, -1);
      }
      return date;
    }

    function easterSunday(year) {
      const a = year % 19;
      const b = Math.floor(year / 100);
      const c = year % 100;
      const d = Math.floor(b / 4);
      const e = b % 4;
      const f = Math.floor((b + 8) / 25);
      const g = Math.floor((b - f + 1) / 3);
      const h = (19 * a + b - d - g + 15) % 30;
      const i = Math.floor(c / 4);
      const k = c % 4;
      const l = (32 + 2 * e + 2 * i - h - k) % 7;
      const m = Math.floor((a + 11 * h + 22 * l) / 451);
      const month = Math.floor((h + l - 7 * m + 114) / 31);
      const day = ((h + l - 7 * m + 114) % 31) + 1;
      return marketDate(year, month, day);
    }

    function addDays(date, days) {
      const base = dateToUtcNoon(normalizeMarketDate(date));
      base.setUTCDate(base.getUTCDate() + days);
      return marketDate(base.getUTCFullYear(), base.getUTCMonth() + 1, base.getUTCDate());
    }

    function dateToUtcNoon(date) {
      const marketDate = normalizeMarketDate(date);
      return new Date(Date.UTC(marketDate.year, marketDate.month - 1, marketDate.day, 12, 0, 0, 0));
    }

    function normalizeMarketDate(date) {
      if (!date) return undefined;
      if (typeof date === 'string') {
        const normalized = normalizeExpiry(date);
        if (!/^\d{8}$/.test(normalized)) return undefined;
        return marketDate(
          Number(normalized.slice(0, 4)),
          Number(normalized.slice(4, 6)),
          Number(normalized.slice(6, 8))
        );
      }
      if (date instanceof Date) {
        if (Number.isNaN(date.getTime())) return undefined;
        return marketDate(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
      }
      if (typeof date === 'object' && Number.isFinite(date.year) && Number.isFinite(date.month) && Number.isFinite(date.day)) {
        return marketDate(date.year, date.month, date.day);
      }
      return undefined;
    }

    function parseExchangeLocalDateTime(value) {
      const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/);
      if (!match) return undefined;
      const [, year, month, day, hour, minute] = match;
      const parts = {
        year: Number(year),
        month: Number(month),
        day: Number(day),
        hour: Number(hour),
        minute: Number(minute)
      };
      if (parts.month < 1 || parts.month > 12 || parts.hour < 0 || parts.hour > 23 || parts.minute < 0 || parts.minute > 59) {
        return undefined;
      }
      const normalized = marketDate(parts.year, parts.month, parts.day);
      if (normalized.year !== parts.year || normalized.month !== parts.month || normalized.day !== parts.day) {
        return undefined;
      }
      return parts;
    }

    function requireExchangeLocalDateTime(value, label) {
      const parts = parseExchangeLocalDateTime(value);
      if (!parts) {
        throw new Error(`${label} must be a valid datetime in YYYY-MM-DDTHH:mm format.`);
      }
      return parts;
    }

    function marketDate(year, month, day) {
      const normalized = new Date(Date.UTC(year, month - 1, day, 12, 0, 0, 0));
      const value = {
        year: normalized.getUTCFullYear(),
        month: normalized.getUTCMonth() + 1,
        day: normalized.getUTCDate()
      };
      value.key = `${value.year}${pad(value.month)}${pad(value.day)}`;
      return value;
    }

    function pad(value) {
      return String(value).padStart(2, '0');
    }
  }

  function addUtcDays(expiry, days) {
    const normalized = normalizeExpiry(expiry);
    if (!/^\d{8}$/.test(normalized)) return normalized;
    const date = new Date(`${normalized.slice(0, 4)}-${normalized.slice(4, 6)}-${normalized.slice(6, 8)}T00:00:00Z`);
    if (Number.isNaN(date.getTime())) return normalized;
    date.setUTCDate(date.getUTCDate() + days);
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const day = String(date.getUTCDate()).padStart(2, '0');
    return `${year}${month}${day}`;
  }

	  function fmtPrice(value) {
	    return Number(value || 0).toFixed(2);
	  }

	  function vixMetaText(quality, timestamp) {
	    const text = String(quality || '--').toUpperCase();
	    if (!timestamp) return text;
	    const time = new Date(timestamp);
	    if (Number.isNaN(time.getTime())) return text;
	    return `${text} ${time.toLocaleTimeString('en-GB', { timeZone: 'Europe/Amsterdam', hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
	  }

	  function esMetaText(size, timestamp) {
	    const sizeText = Number.isFinite(size) && size > 0 ? `SIZE ${size}` : 'SIZE --';
	    if (!timestamp) return sizeText;
	    const time = new Date(timestamp);
	    if (Number.isNaN(time.getTime())) return sizeText;
	    return `${sizeText} ${time.toLocaleTimeString('en-GB', { timeZone: 'Europe/Amsterdam', hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
	  }

  function midPrice(metrics) {
    const bid = Number(metrics?.bid || 0);
    const ask = Number(metrics?.ask || 0);
    if (bid > 0 && ask > 0) return (bid + ask) / 2;
    return Number(metrics?.last || 0);
  }

		                  function selectedSpreadTicket(data, selectedSpread) {
		                    if (!selectedSpread) return undefined;
		                    const rowsByStrike = new Map(data.map(row => [Number(row.strike), row]));
		                    const row = rowsByStrike.get(Number(selectedSpread.strike));
		                    return row
		                      ? spreadDetails(row, rowsByStrike, selectedSpread.side) || spreadTemplate(selectedSpread.side, Number(selectedSpread.strike))
		                      : spreadTemplate(selectedSpread.side, Number(selectedSpread.strike));
		                  }

		                  function spreadCellProps(side, strike, spread, selectedSpread, onSelectSpread, sandwichCell) {
		                    const selected = selectedSpread?.side === side && Number(selectedSpread?.strike) === Number(strike);
		                    const className = [
		                      side === 'call' ? 'mid-call' : 'mid-put',
		                      'spread-selectable',
		                      selected ? 'spread-selected' : '',
		                      sandwichCell?.className || ''
		                    ].filter(Boolean).join(' ');
		                    const props = {
		                      className,
		                      title: [spreadTitle(strike, side, spread?.credit), sandwichCell?.title].filter(Boolean).join(' | ')
		                    };
		                    const select = () => onSelectSpread(selected ? undefined : { side, strike: Number(strike) });
		                    props.role = 'button';
	                    props.tabIndex = 0;
	                    props['aria-pressed'] = selected;
	                    props.onClick = select;
	                    props.onKeyDown = event => {
	                      if (event.key === 'Enter' || event.key === ' ') {
	                        event.preventDefault();
	                        select();
	                      }
	                    };
		                    return props;
		                  }

	                  function spreadCredit(row, rowsByStrike, side) {
	                    return spreadDetails(row, rowsByStrike, side)?.credit;
	                  }

		                  function spreadDetails(row, rowsByStrike, side) {
		                    const strike = Number(row?.strike);
		                    if (!Number.isFinite(strike)) return undefined;
	                    const shortLeg = side === 'call' ? row?.call : row?.put;
	                    const longStrike = side === 'call' ? strike + wingWidth : strike - wingWidth;
	                    const longRow = rowsByStrike.get(longStrike);
	                    const longLeg = side === 'call' ? longRow?.call : longRow?.put;
	                    const shortBid = Number(shortLeg?.bid || 0);
	                    const shortAsk = Number(shortLeg?.ask || 0);
	                    const longBid = Number(longLeg?.bid || 0);
	                    const longAsk = Number(longLeg?.ask || 0);
	                    if (shortBid < 0 || shortAsk <= 0 || longBid < 0 || longAsk <= 0) return undefined;
	                    const creditBid = shortBid - longAsk;
	                    const creditAsk = shortAsk - longBid;
	                    const creditMid = (creditBid + creditAsk) / 2;
		                    if (creditMid <= 0) return undefined;
		                    return { ...spreadTemplate(side, strike), credit: creditMid };
		                  }

		                  function spreadTemplate(side, strike) {
		                    if (!Number.isFinite(strike)) return undefined;
		                    return {
		                      side,
		                      strike,
		                      strategy: side === 'call' ? 'Bear Call Spread' : 'Bull Put Spread',
		                      shortStrike: strike,
		                      longStrike: side === 'call' ? strike + wingWidth : strike - wingWidth,
		                      credit: undefined
		                    };
		                  }

	                  function fmtCredit(value) {
	                    return Number.isFinite(value) ? value.toFixed(2) : '--';
	                  }

	                  function fmtStrength(value) {
	                    const number = Number(value);
	                    return Number.isFinite(number) ? number.toFixed(2) : '--';
	                  }

	                  function amsterdamTimeLabel() {
	                    return new Intl.DateTimeFormat('en-GB', {
	                      timeZone: 'Europe/Amsterdam',
	                      hour: '2-digit',
	                      minute: '2-digit',
	                      second: '2-digit',
	                      hour12: false
	                    }).format(new Date());
	                  }

		                  function spreadTitle(strike, side, credit) {
	                    const number = Number(strike);
	                    const creditText = fmtCredit(credit);
	                    if (side === 'call') {
	                      return `Bear call spread ${number}/${number + wingWidth} mid credit: ${creditText}`;
	                    }
	                    return `Bull put spread ${number}/${number - wingWidth} mid credit: ${creditText}`;
	                  }

  function fmtVolume(value) {
    const number = Number(value || 0);
    if (number >= 1_000_000) return `${(number / 1_000_000).toFixed(1)}M`;
    if (number >= 1_000) return `${(number / 1_000).toFixed(1)}k`;
    return `${number}`;
  }

  function fmtSummaryVolume(value) {
    const number = positiveNumber(value);
    return Math.round(number).toLocaleString('en-US');
  }

  function fmtPressure(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return '--';
    const abs = Math.abs(number);
    if (abs >= 1_000_000) return `${stripTrailingZero((number / 1_000_000).toFixed(1))}M`;
    if (abs >= 1_000) return `${stripTrailingZero((number / 1_000).toFixed(1))}k`;
    return stripTrailingZero(number.toFixed(1));
  }

  function fmtSignedPressure(value) {
    const number = Number(value);
    const text = fmtPressure(value);
    if (text === '--' || number <= 0) return text;
    return `+${text}`;
  }

  function pressureSignalText(signal) {
    return String(signal || '--').replaceAll('_', ' ');
  }

  function pressureSignalClass(signal) {
    return `pressure-${String(signal || 'none').toLowerCase().replaceAll('_', '-')}`;
  }

  function putCallRatioLabel(value) {
    if (value === Number.POSITIVE_INFINITY) return 'put-only volume';
    const ratio = Number(value);
    if (!Number.isFinite(ratio)) return 'waiting for volume';
    if (ratio > 1.80) return 'extreme fear, possible reversal later';
    if (ratio > 1.20) return 'bearish / put-dominant';
    if (ratio < 0.70) return 'bullish / call-dominant';
    return 'neutral';
  }

  function putCallRatioValue(putVolume, callVolume) {
    const put = positiveNumber(putVolume);
    const call = positiveNumber(callVolume);
    if (call > 0) return put / call;
    return put > 0 ? Number.POSITIVE_INFINITY : undefined;
  }

  function putCallRatioText(value) {
    if (value === Number.POSITIVE_INFINITY) return '∞';
    const ratio = Number(value);
    return Number.isFinite(ratio) ? ratio.toFixed(5) : '--';
  }

  function totalVolumeForSide(rows, side) {
    return rows.reduce((sum, row) => sum + positiveNumber(row?.[side]?.volume), 0);
  }

  function directionalPressureForWindow(directionalPressure, windowName) {
    if (!directionalPressure) return undefined;
    const windows = Array.isArray(directionalPressure.windows) ? directionalPressure.windows : [];
    return windows.find(window => window.window === windowName) || directionalPressure;
  }

  function totalPaceForWindow(rows, paceFields) {
    return rows.reduce((totals, row) => {
      totals.call += positiveNumber(row[paceFields.call]);
      totals.put += positiveNumber(row[paceFields.put]);
      return totals;
    }, { call: 0, put: 0 });
  }

  function positiveNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) && number > 0 ? number : 0;
  }

	  function fmtPace(value, paceWindow) {
	    const number = Math.max(0, Number(value || 0));
	    if (!Number.isFinite(number) || number === 0) return '0/m';
	    if (number >= 1_000) return `${stripTrailingZero((number / 1_000).toFixed(1))}k/m`;
	    if (number >= 100) return `${Math.round(number)}/m`;
	    if (['1h', '4h', '8h'].includes(paceWindow)) return `${stripTrailingZero(number.toFixed(2))}/m`;
	    return `${stripTrailingZero(number.toFixed(1))}/m`;
	  }

	  function fmtPaceChange(value, paceWindow) {
	    const number = Number(value || 0);
	    if (!Number.isFinite(number) || number === 0) return '0/m';
	    const sign = number > 0 ? '+' : '-';
	    return `${sign}${fmtPace(Math.abs(number), paceWindow)}`;
	  }

	  function paceChangeClass(value) {
	    const number = Number(value || 0);
	    if (!Number.isFinite(number) || number === 0) return 'pace-change-flat';
	    return number > 0 ? 'pace-change-up' : 'pace-change-down';
	  }

	  function paceWindowFields(windowName) {
	    switch (windowName) {
	      case '10s':
	        return { call: 'callPace10s', put: 'putPace10s', callChange: 'callPaceChange10s', putChange: 'putPaceChange10s' };
	      case '10m':
	        return { call: 'callPace10m', put: 'putPace10m', callChange: 'callPaceChange10m', putChange: 'putPaceChange10m' };
	      case '15m':
	        return { call: 'callPace15m', put: 'putPace15m', callChange: 'callPaceChange15m', putChange: 'putPaceChange15m' };
	      case '30m':
	        return { call: 'callPace30m', put: 'putPace30m', callChange: 'callPaceChange30m', putChange: 'putPaceChange30m' };
	      case '1h':
	        return { call: 'callPace1h', put: 'putPace1h', callChange: 'callPaceChange1h', putChange: 'putPaceChange1h' };
	      case '4h':
	        return { call: 'callPace4h', put: 'putPace4h', callChange: 'callPaceChange4h', putChange: 'putPaceChange4h' };
	      case '8h':
	        return { call: 'callPace8h', put: 'putPace8h', callChange: 'callPaceChange8h', putChange: 'putPaceChange8h' };
	      case '1m':
	      default:
	        return { call: 'callPace1m', put: 'putPace1m', callChange: 'callPaceChange1m', putChange: 'putPaceChange1m' };
	    }
	  }

	  function stripTrailingZero(value) {
    return String(value).replace(/\.0$/, '');
  }

  function fmtGex(value) {
    const number = Number(value || 0);
    const abs = Math.abs(number);
    if (abs >= 1_000_000_000) return `${(number / 1_000_000_000).toFixed(2)}B`;
    if (abs >= 1_000_000) return `${(number / 1_000_000).toFixed(2)}M`;
    if (abs >= 1_000) return `${(number / 1_000).toFixed(2)}K`;
    return number.toFixed(2);
  }

	  function fmtSignedGex(value) {
	    const number = Number(value || 0);
	    const text = fmtGex(number);
	    return number > 0 ? `+${text}` : text;
	  }

  function fmtSignedUsdGex(value) {
    const number = Number(value || 0);
    const text = fmtGex(Math.abs(number));
    if (number > 0) return `+${text}`;
    if (number < 0) return `-${text}`;
    return '0.00';
  }

  function formatTime(value) {
    const date = new Date(value || '');
    if (Number.isNaN(date.getTime())) return 'unknown';
    return date.toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    });
  }

	  function strikeRowTitle(row) {
	    return `Strike ${row.strike}`;
	  }

  function normalizeGexPayload(payload) {
    return {
      strike: Number(payload?.strike),
      symbol: payload?.symbol,
      expiry: payload?.expiry,
      source: payload?.source || payload?.marketDataSource,
      marketDataSource: payload?.marketDataSource || payload?.source,
      uwGexExpiry: payload?.expiry,
      uwCallGex: Number(payload?.callGex || 0),
	      uwPutGex: Number(payload?.putGex || 0),
	      uwNetGex: Number(payload?.netGex || 0),
	      uwGammaSign: String(payload?.gammaSign || '').toUpperCase(),
	      uwGexTimeframe: String(payload?.timeframe || '1D').toUpperCase(),
	      uwGexUpdatedAt: payload?.updatedAt,
      uwGexStaleAfterMs: Number(payload?.staleAfterMs || 90_000),
      uwGexHistory: normalizeGexHistory(payload?.history, payload?.source || payload?.marketDataSource)
    };
  }

  function normalizeGexHistory(history, source) {
    const map = history || {};
    return gexHistoryWindowsForSource(source).reduce((normalized, windowName) => {
      const item = map[windowName];
      normalized[windowName] = normalizeGexHistoryItem(item);
      return normalized;
    }, {});
  }

  function normalizeGexHistoryItem(item) {
    if (!item || typeof item !== 'object') {
      return {};
    }
    return {
      netGex: Number(item.netGex),
      delta: Number(item.delta),
      direction: String(item.direction || '').toUpperCase(),
      sampledAt: item.sampledAt
    };
  }

  function rowMapKeyFor(map, strike) {
    if (map.has(strike)) return strike;
    const number = Number(strike);
    if (Number.isFinite(number) && map.has(number)) return number;
    return strike;
  }

	  function computeGexStatus(databentoMode, total, summary) {
	    const visible = summary.visibleCount;
	    const stale = summary.staleCount;
	    if (!databentoMode) {
	      // Unusual Whales GEX is an optional overlay; preserve legacy semantics.
	      if (total > 0 && visible === 0) {
	        return { state: 'optional', className: 'disabled', label: `GEX optional 0/${total}`, reason: 'no Unusual Whales exposure' };
	      }
	      const className = stale > 0 ? 'stale' : (visible === total && total > 0 ? 'ready' : '');
	      const state = stale > 0 ? 'stale' : (visible === total && total > 0 ? 'ready' : 'waiting');
	      return { state, className, label: `GEX ${visible}/${total} UW${stale ? ` stale ${stale}` : ''}`, reason: '' };
	    }
	    // Databento GEX: explicit, operator-readable status states.
	    if (total === 0) {
	      return { state: 'waiting', className: 'waiting', label: 'GEX waiting DBN', reason: 'waiting for option chain' };
	    }
	    if (visible === 0) {
	      return { state: 'unavailable', className: 'unavailable', label: `GEX unavailable 0/${total} DBN`, reason: 'no Databento GEX records (producer not running or topic empty)' };
	    }
	    if (stale >= visible) {
	      return { state: 'stale', className: 'stale', label: `GEX stale ${visible}/${total} DBN`, reason: 'all GEX records stale' };
	    }
	    if (visible < total) {
	      return { state: 'partial', className: 'partial', label: `GEX partial ${visible}/${total} DBN${stale ? ` stale ${stale}` : ''}`, reason: `partial strike coverage${stale ? `, ${stale} stale` : ''}` };
	    }
	    if (stale > 0) {
	      return { state: 'partial', className: 'partial', label: `GEX ${visible}/${total} DBN stale ${stale}`, reason: `${stale} stale records` };
	    }
	    return { state: 'ready', className: 'ready', label: `GEX ${visible}/${total} DBN`, reason: '' };
	  }

	  function gexSourceMeta(row) {
	    const src = String((row && (row.source || row.marketDataSource)) || '').toUpperCase();
	    if (src === 'DATABENTO') {
	      return { name: 'databento', short: 'DBN', display: 'Databento' };
	    }
	    return { name: 'unusual-whales', short: 'UW', display: 'Unusual Whales' };
	  }

	  function gexStrikeState(row, maxAbsGex) {
	    const hasGex = row?.uwGexUpdatedAt || row?.uwGammaSign || Number.isFinite(Number(row?.uwNetGex));
	    if (!hasGex) {
	      return { className: '', stale: false, visible: false, text: '', title: 'Waiting for gamma exposure' };
	    }
	    const updatedAtMs = Date.parse(row.uwGexUpdatedAt || '');
	    const staleAfterMs = Number(row.uwGexStaleAfterMs || 90_000);
	    const stale = !Number.isFinite(updatedAtMs) || Date.now() - updatedAtMs > staleAfterMs;
	    const sign = String(row.uwGammaSign || '').toUpperCase();
	    const netGex = Number(row.uwNetGex || 0);
	    const timeframe = String(row.uwGexTimeframe || '1D').toUpperCase();
	    const change = Number(row.uwGexChange);
	    const strengthPercent = gexStrengthPercent(netGex, maxAbsGex);
	    const fillPercent = Math.max(strengthPercent > 0 ? 2 : 0, Math.round(strengthPercent / 2));
	    const arrowPosition = gexArrowPosition(netGex, maxAbsGex);
	    const moveDirection = String(row.uwGexMoveDirection || gexHorizontalDirection(change)).toUpperCase();
	    const arrow = gexHorizontalArrow(moveDirection);
	    const arrowClass = gexHorizontalArrowClass(moveDirection);
	    const changeText = Number.isFinite(change) ? fmtSignedUsdGex(change) : 'waiting';
	    const changeTitle = `Net exposure ${Number.isFinite(change) ? 'changed ' + changeText : 'waiting for next tick'} since previous update`;
	    const className = stale
	      ? 'gamma-stale'
	      : sign === 'NEGATIVE'
	        ? 'gamma-negative'
	        : sign === 'POSITIVE'
	          ? 'gamma-positive'
	          : 'gamma-neutral';
	    const title = `${gexSourceMeta(row).display} ${timeframe} ${sign || 'UNKNOWN'} net ${fmtSignedUsdGex(netGex)} | ${changeTitle} | updated ${row.uwGexUpdatedAt || 'unknown'}`;
	    return {
	      className,
	      stale,
	      visible: true,
	      text: fmtSignedUsdGex(netGex),
	      title,
	      strengthPercent,
	      moveDirection,
	      arrow,
	      arrowClass,
	      changeTitle,
	      style: {
	        '--gex-fill-width': `${fillPercent}%`,
	        '--gex-arrow-left': `${arrowPosition}%`,
	        '--gex-timeframe-label': `"${timeframe}"`
	      }
	    };
	  }

	  function maxAbsNetGex(rows) {
	    return rows.reduce((max, row) => {
	      const value = Math.abs(Number(row?.uwNetGex || 0));
	      return Number.isFinite(value) ? Math.max(max, value) : max;
	    }, 0);
	  }

	  function gexStrengthPercent(netGex, maxAbsGex) {
	    const max = Number(maxAbsGex || 0);
	    const value = Math.abs(Number(netGex || 0));
	    if (!Number.isFinite(max) || max <= 0 || !Number.isFinite(value)) return 0;
	    return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
	  }

	  function gexArrowPosition(netGex, maxAbsGex) {
	    const max = Number(maxAbsGex || 0);
	    const value = Number(netGex || 0);
	    if (!Number.isFinite(max) || max <= 0 || !Number.isFinite(value)) return 50;
	    return Math.max(4, Math.min(96, Math.round(50 + (value / max) * 46)));
	  }

	  function gexHorizontalDirection(delta) {
	    const value = Number(delta || 0);
	    if (!Number.isFinite(value) || value === 0) return 'FLAT';
	    return value > 0 ? 'RIGHT' : 'LEFT';
	  }

	  function gexHorizontalArrow(direction) {
	    const value = String(direction || '').toUpperCase();
	    if (value === 'RIGHT') return '→';
	    if (value === 'LEFT') return '←';
	    return '';
	  }

	  function gexHorizontalArrowClass(direction) {
	    const value = String(direction || '').toUpperCase();
	    if (value === 'RIGHT') return 'gex-arrow-right';
	    if (value === 'LEFT') return 'gex-arrow-left';
	    return 'gex-arrow-flat';
	  }

	  function gexHistoryRows(row) {
    const currentNetGex = Number(row?.uwNetGex || 0);
    return gexHistoryWindowsForSource(row?.source || row?.marketDataSource).map(windowName => {
      const item = row?.uwGexHistory?.[windowName] || {};
      const historicalNetGex = Number(item.netGex);
      if (!Number.isFinite(historicalNetGex)) {
        return { window: windowName, available: false };
      }
      const delta = Number.isFinite(Number(item.delta))
        ? Number(item.delta)
        : currentNetGex - historicalNetGex;
      const direction = item.direction || gexDeltaDirection(delta);
      return {
        window: windowName,
        available: true,
		        valueText: fmtSignedUsdGex(historicalNetGex),
	        deltaText: fmtSignedUsdGex(delta),
        arrow: gexDeltaArrow(direction),
        deltaClass: gexDeltaClass(direction)
      };
    });
  }

  function gexDeltaDirection(delta) {
    const number = Number(delta || 0);
    if (!Number.isFinite(number) || number === 0) return 'FLAT';
    return number > 0 ? 'UP' : 'DOWN';
  }

  function gexDeltaArrow(direction) {
    const value = String(direction || '').toUpperCase();
    if (value === 'UP') return '↑';
    if (value === 'DOWN') return '↓';
    return '→';
  }

  function gexDeltaClass(direction) {
    const value = String(direction || '').toUpperCase();
    if (value === 'UP') return 'gex-delta-up';
    if (value === 'DOWN') return 'gex-delta-down';
    return 'gex-delta-flat';
  }

  function unusualWhalesGexSummary(rows) {
    return rows.reduce((summary, row) => {
      const gex = gexStrikeState(row);
      if (gex.visible) {
        summary.visibleCount += 1;
      } else {
        summary.missingCount += 1;
      }
      if (gex.stale) summary.staleCount += 1;
      return summary;
    }, { visibleCount: 0, staleCount: 0, missingCount: 0 });
  }

	  function activeStrikeRows(rows, spot, maxStrikes) {
	    const limit = Math.max(1, Math.floor(Number(maxStrikes || rows.length)));
	    if (!rows.length || !Number.isFinite(limit) || rows.length <= limit) return rows;
    const spotNumber = Number(spot);
    if (!Number.isFinite(spotNumber)) return rows.slice(0, limit);
    return [...rows]
      .sort((left, right) => {
        const leftStrike = Number(left.strike);
        const rightStrike = Number(right.strike);
        const distance = Math.abs(leftStrike - spotNumber) - Math.abs(rightStrike - spotNumber);
        return distance || leftStrike - rightStrike;
      })
	      .slice(0, limit)
	      .sort((left, right) => Number(left.strike) - Number(right.strike));
	  }

  function stableActiveStrikeRows(rows, spot, maxStrikes, visibleStrikesRef) {
    const limit = Math.max(1, Math.floor(Number(maxStrikes || rows.length)));
    if (!rows.length || !Number.isFinite(limit) || rows.length <= limit) {
      visibleStrikesRef.current = [];
      return rows;
    }

    const byStrike = new Map(rows.map(row => [Number(row.strike), row]));
    const stableStrikes = Array.isArray(visibleStrikesRef.current) ? visibleStrikesRef.current : [];
    const stableRows = stableStrikes.map(strike => byStrike.get(strike)).filter(Boolean);
    if (stableStrikes.length === limit && stableRows.length === limit) {
      return stableRows.sort((left, right) => Number(left.strike) - Number(right.strike));
    }

    const selectedRows = stableStrikes.length === limit
      ? refillStableRows(rows, stableRows, stableStrikes, spot, limit)
      : activeStrikeRows(rows, spot, limit);
    visibleStrikesRef.current = selectedRows.map(row => Number(row.strike)).filter(Number.isFinite);
    return selectedRows.sort((left, right) => Number(left.strike) - Number(right.strike));
  }

  function refillStableRows(rows, stableRows, stableStrikes, spot, limit) {
    const stableStrikeSet = new Set(stableStrikes);
    const missingCount = Math.max(0, limit - stableRows.length);
    if (missingCount <= 0) return stableRows;
    const backfillRows = activeStrikeRows(
      rows.filter(row => !stableStrikeSet.has(Number(row.strike))),
      spot,
      missingCount
    );
    return [...stableRows, ...backfillRows].slice(0, limit);
  }

	  function nearestStrikeIndex(data, spot) {
	                    if (!data.length) return -1;
	                    const spotNumber = Number(spot);
	                    if (!Number.isFinite(spotNumber)) return 0;
    let bestIndex = 0;
    let bestDistance = Math.abs(Number(data[0].strike) - spotNumber);
    for (let i = 1; i < data.length; i += 1) {
      const strike = Number(data[i].strike);
      if (!Number.isFinite(strike)) continue;
      const distance = Math.abs(strike - spotNumber);
      if (distance < bestDistance) {
        bestIndex = i;
        bestDistance = distance;
      }
	                    }
	                    return bestIndex;
	                  }

		                  function spotLinePosition(data, spot, stage, body) {
		                    if (!data.length) return undefined;
		                    const spotNumber = Number(spot);
		                    if (!Number.isFinite(spotNumber)) return undefined;
		                    const strikes = data.map(row => Number(row.strike));
		                    const rows = body ? Array.from(body.querySelectorAll('tr')) : [];
		                    const stageRect = stage?.getBoundingClientRect();
		                    const lastIndex = strikes.length - 1;
		                    if (!Number.isFinite(strikes[0]) || !Number.isFinite(strikes[lastIndex])) return undefined;

		                    const rowCenter = index => {
		                      const row = rows[index];
		                      if (row && stageRect) {
		                        const rowRect = row.getBoundingClientRect();
		                        return rowRect.top - stageRect.top + rowRect.height / 2;
		                      }
		                      return fallbackSpotLinePosition(index);
		                    };

		                    const ascending = strikes[0] <= strikes[lastIndex];
		                    if ((ascending && spotNumber <= strikes[0]) || (!ascending && spotNumber >= strikes[0])) {
		                      return rowCenter(0);
		                    }
		                    if ((ascending && spotNumber >= strikes[lastIndex]) || (!ascending && spotNumber <= strikes[lastIndex])) {
		                      return rowCenter(lastIndex);
		                    }

		                    for (let i = 0; i < lastIndex; i += 1) {
		                      const start = strikes[i];
		                      const end = strikes[i + 1];
		                      if (!Number.isFinite(start) || !Number.isFinite(end)) continue;
		                      if (insideStrikeSegment(spotNumber, start, end)) {
		                        const span = end - start;
		                        const fraction = span === 0 ? 0 : (spotNumber - start) / span;
		                        const startCenter = rowCenter(i);
		                        const endCenter = rowCenter(i + 1);
		                        return startCenter + ((endCenter - startCenter) * fraction);
		                      }
		                    }

		                    const nearestIndex = nearestStrikeIndex(data, spotNumber);
		                    return nearestIndex < 0 ? undefined : rowCenter(nearestIndex);
		                  }

		                  function fallbackSpotLinePosition(index) {
		                    const rowHeight = 34;
		                    const headerHeight = 38;
		                    return headerHeight + (index * rowHeight) + rowHeight / 2;
		                  }

		                  function insideStrikeSegment(value, start, end) {
		                    return start <= end
		                      ? value >= start && value <= end
		                      : value <= start && value >= end;
		                  }

		                  function samePosition(currentTop, nextTop) {
		                    if (!Number.isFinite(currentTop) && !Number.isFinite(nextTop)) return true;
		                    if (!Number.isFinite(currentTop) || !Number.isFinite(nextTop)) return false;
		                    return Math.abs(currentTop - nextTop) < 0.5;
		                  }

	                  function centerCurrentStrike(timers, wrap, body, atmIndex, force = false) {
    timers.forEach(window.clearTimeout);
    timers.length = 0;
    [0, 80, 220, 600].forEach(delay => {
      timers.push(window.setTimeout(() => centerCurrentStrikeOnce(wrap, body, atmIndex, force), delay));
    });
  }

  function centerCurrentStrikeOnce(wrap, body, atmIndex, force = false) {
    window.requestAnimationFrame(() => {
      if (!wrap || !body) return;
      const bodyRows = Array.from(body.querySelectorAll('tr'));
      const row = atmIndex >= 0 ? bodyRows[atmIndex] : wrap.querySelector('tr.current-strike');
      if (!row) return;

      const rowHeight = row.offsetHeight || bodyRows[0]?.offsetHeight || 34;
      const nextScrollTop = centerScrollTop(atmIndex, rowHeight, wrap.clientHeight, wrap.scrollHeight);
      const maxScrollTop = Math.max(0, wrap.scrollHeight - wrap.clientHeight);
      if (maxScrollTop > 1) {
        wrap.scrollTop = nextScrollTop;
        if (Math.abs(wrap.scrollTop - nextScrollTop) > 1) {
          wrap.scrollTo({ top: nextScrollTop, behavior: 'auto' });
        }
        centerRowByGeometry(wrap, row);
        window.requestAnimationFrame(() => centerRowByGeometry(wrap, row));
      }

      const nextWrapRect = wrap.getBoundingClientRect();
      const nextRowRect = row.getBoundingClientRect();
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
      const hiddenInWrap = nextRowRect.top < nextWrapRect.top || nextRowRect.bottom > nextWrapRect.bottom;
      const hiddenInViewport = nextRowRect.top < 0 || nextRowRect.bottom > viewportHeight;
      if (maxScrollTop <= 1 || hiddenInWrap || hiddenInViewport) {
        row.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' });
        if (force) {
          window.requestAnimationFrame(() => centerRowByGeometry(wrap, row));
        }
      }
    });
  }

  function centerRowByGeometry(wrap, row) {
    const maxScrollTop = Math.max(0, wrap.scrollHeight - wrap.clientHeight);
    const wrapRect = wrap.getBoundingClientRect();
    const rowRect = row.getBoundingClientRect();
    const offset = (rowRect.top + rowRect.height / 2) - (wrapRect.top + wrapRect.height / 2);
    if (!Number.isFinite(offset)) return;
    const nextScrollTop = Math.max(0, Math.min(maxScrollTop, wrap.scrollTop + offset));
    if (Math.abs(wrap.scrollTop - nextScrollTop) > 0.5) {
      wrap.scrollTop = nextScrollTop;
    }
  }

  function centerScrollTop(atmIndex, rowHeight, viewportHeight, scrollHeight) {
    if (atmIndex < 0 || rowHeight <= 0 || viewportHeight <= 0 || scrollHeight <= viewportHeight) {
      return 0;
    }
    const maxScrollTop = Math.max(0, scrollHeight - viewportHeight);
    const rowTop = atmIndex * rowHeight;
    const targetTop = rowTop - ((viewportHeight - rowHeight) / 2);
    return Math.max(0, Math.min(maxScrollTop, targetTop));
  }

  function highestPaceStrike(data, field) {
    const target = data.reduce((best, row) => betterPaceTarget(best, paceTarget(row, field)), undefined);
    return target?.strike;
  }

  function topVolumeStrikes(data, side) {
    return new Set(data
      .map(row => ({ strike: Number(row.strike), volume: Number(row[side]?.volume || 0) }))
      .filter(row => Number.isFinite(row.strike) && row.volume > 0)
      .sort((left, right) => right.volume - left.volume || left.strike - right.strike)
      .slice(0, 3)
      .map(row => row.strike));
  }

  function paceTarget(row, field) {
    return { strike: row.strike, value: Number(row[field] || 0) };
  }

  function betterPaceTarget(best, candidate) {
    if (!candidate || candidate.value <= 0) return best;
    if (!best || candidate.value > best.value) return candidate;
    if (candidate.value < best.value) return best;
    return candidate.strike < best.strike ? candidate : best;
  }

  function paceScore(value, maxValue) {
    if (!value || value <= 0 || !maxValue || maxValue <= 0) return 0;
    return Math.max(0, Math.min(100, Math.round((value / maxValue) * 100)));
  }

  window.OptionChainCentering = { nearestStrikeIndex, centerScrollTop, spotLinePosition };
  window.OptionChainMetrics = { totalVolumeForSide, putCallRatioValue, putCallRatioText, fmtSummaryVolume };
  ReactDOM.createRoot(rootElement).render(h(OptionChainApp));
})();
