# Private diagnostic only; included by the pinned DEX reproducer.
.method private logLdySipDiagnostic(Lcom/mediatek/ims/SipMessage;[Ljava/lang/String;)V
    .registers 10

    sget-boolean v0, Landroid/os/Build;->IS_DEBUGGABLE:Z
    if-eqz v0, :ldy_done
    iget v0, p0, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->mLdySipDiagnosticRecords:I
    const/16 v1, 0x40
    if-ge v0, v1, :ldy_done

    :ldy_try_start
    const/4 v0, 0x0
    aget-object v0, p2, v0
    invoke-static {v0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
    move-result v1
    iget-object v0, p0, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->this$0:Lcom/mediatek/ims/ImsCallSessionProxy;
    invoke-static {v0}, Lcom/mediatek/ims/ImsCallSessionProxy;->-$$Nest$fgetmPhoneId(Lcom/mediatek/ims/ImsCallSessionProxy;)I
    move-result v0
    invoke-virtual {p1}, Lcom/mediatek/ims/SipMessage;->getDir()I
    move-result v2
    invoke-virtual {p1}, Lcom/mediatek/ims/SipMessage;->getType()I
    move-result v3
    invoke-virtual {p1}, Lcom/mediatek/ims/SipMessage;->getMethod()I
    move-result v4
    invoke-virtual {p1}, Lcom/mediatek/ims/SipMessage;->getCode()I
    move-result v5
    new-instance v6, Ljava/lang/StringBuilder;
    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V
    const-string p1, "slot="
    invoke-virtual {v6, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v0, " callId="
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v0, " direction="
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v0, " type="
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v0, " method="
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v0, " code="
    invoke-virtual {v6, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v6, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    const-string v1, "LdyImsSipDiag"
    invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    move-result v0
    if-lez v0, :ldy_try_end
    iget v0, p0, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->mLdySipDiagnosticRecords:I
    add-int/lit8 v0, v0, 0x1
    iput v0, p0, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->mLdySipDiagnosticRecords:I
    :ldy_try_end
    .catch Ljava/lang/RuntimeException; {:ldy_try_start .. :ldy_try_end} :ldy_failure
    :ldy_done
    return-void
    :ldy_failure
    move-exception v0
    return-void
.end method
