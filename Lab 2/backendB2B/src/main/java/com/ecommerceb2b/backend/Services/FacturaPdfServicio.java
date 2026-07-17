package com.ecommerceb2b.backend.Services;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.ecommerceb2b.backend.Entities.CarritoProductoEntidad;
import com.ecommerceb2b.backend.Entities.FacturaEntidad;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

@Service
public class FacturaPdfServicio {

    private static final Color COLOR_PRIMARIO = new Color(21, 104, 149);
    private static final Color COLOR_CABECERA_TABLA = new Color(21, 104, 149);
    private static final Color COLOR_FILA_PAR = new Color(248, 250, 252);
    private static final Color COLOR_TEXTO_GRIS = new Color(71, 85, 105);

    public byte[] generarPdf(FacturaEntidad factura) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 70, 50);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        agregarEncabezado(doc, factura);
        agregarEspaciado(doc, 10);
        agregarInfoGeneral(doc, factura);
        agregarEspaciado(doc, 16);
        agregarTablaProductos(doc, factura.getItems());
        agregarEspaciado(doc, 16);
        agregarResumenTotales(doc, factura);
        agregarEspaciado(doc, 24);
        agregarPiePagina(doc);

        doc.close();
        return baos.toByteArray();
    }

    // ── Encabezado ────────────────────────────────────────────────────────────

    private void agregarEncabezado(Document doc, FacturaEntidad factura) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{2f, 1f});

        // Celda izquierda: nombre empresa
        Font fEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_PRIMARIO);
        Font fSlogan = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_TEXTO_GRIS);

        Paragraph empresa = new Paragraph();
        empresa.add(new Chunk("EcommerceB2B\n", fEmpresa));
        empresa.add(new Chunk("Plataforma de comercio entre empresas", fSlogan));

        PdfPCell celdaEmpresa = new PdfPCell(empresa);
        celdaEmpresa.setBorder(Rectangle.NO_BORDER);
        celdaEmpresa.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaEmpresa);

        // Celda derecha: número de factura
        Font fTituloFactura = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARIO);
        Font fNumero = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);

        Paragraph numFactura = new Paragraph();
        numFactura.add(new Chunk("FACTURA\n", fTituloFactura));
        numFactura.add(new Chunk("N° " + factura.getFactura_ID(), fNumero));
        numFactura.setAlignment(Element.ALIGN_RIGHT);

        PdfPCell celdaNumero = new PdfPCell(numFactura);
        celdaNumero.setBorder(Rectangle.NO_BORDER);
        celdaNumero.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celdaNumero.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaNumero);

        doc.add(tabla);

        // Línea separadora
        LineSeparator linea = new LineSeparator(2, 100, COLOR_PRIMARIO, Element.ALIGN_CENTER, -5);
        doc.add(new Chunk(linea));
    }

    // ── Info general ──────────────────────────────────────────────────────────

    private void agregarInfoGeneral(Document doc, FacturaEntidad factura) throws DocumentException {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1f, 1f});
        tabla.setSpacingBefore(12);

        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_TEXTO_GRIS);
        Font fValor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);

        String fechaFormateada = factura.getFecha_Emision() != null
            ? new SimpleDateFormat("dd/MM/yyyy HH:mm").format(factura.getFecha_Emision())
            : "—";

        agregarParInfoGeneral(tabla, "ID Cliente:", String.valueOf(factura.getUsuarioId()), fLabel, fValor);
        agregarParInfoGeneral(tabla, "N° Orden:", String.valueOf(factura.getOrdenId()), fLabel, fValor);
        agregarParInfoGeneral(tabla, "Fecha de emisión:", fechaFormateada, fLabel, fValor);
        agregarParInfoGeneral(tabla, "Estado:", "EMITIDA", fLabel, fValor);

        doc.add(tabla);
    }

    private void agregarParInfoGeneral(PdfPTable tabla, String label, String valor,
                                       Font fLabel, Font fValor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "  ", fLabel));
        p.add(new Chunk(valor, fValor));

        PdfPCell celda = new PdfPCell(p);
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPaddingBottom(6);
        tabla.addCell(celda);
    }

    // ── Tabla de productos ────────────────────────────────────────────────────

    private void agregarTablaProductos(Document doc, List<CarritoProductoEntidad> items)
            throws DocumentException {

        Font fTituloSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARIO);
        Paragraph tituloSeccion = new Paragraph("Detalle de Productos", fTituloSeccion);
        tituloSeccion.setSpacingAfter(6);
        doc.add(tituloSeccion);

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{3.5f, 1f, 1.5f, 1.5f});

        // Cabecera
        Font fCab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        agregarCeldaCabecera(tabla, "Producto", fCab, Element.ALIGN_LEFT);
        agregarCeldaCabecera(tabla, "Cant.", fCab, Element.ALIGN_CENTER);
        agregarCeldaCabecera(tabla, "Precio unit.", fCab, Element.ALIGN_RIGHT);
        agregarCeldaCabecera(tabla, "Subtotal", fCab, Element.ALIGN_RIGHT);

        // Filas de items
        Font fCelda = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        boolean filapar = false;
        if (items != null) {
            for (CarritoProductoEntidad item : items) {
                Color fondo = filapar ? COLOR_FILA_PAR : Color.WHITE;
                String nombre = item.getProducto() != null
                    ? item.getProducto().getNombre_producto() : "—";
                float precio = item.getProducto() != null
                    ? item.getProducto().getPrecio() : 0f;
                long cantidad = item.getUnidad_producto() != null ? item.getUnidad_producto() : 0L;
                float subtotal = precio * cantidad;

                agregarCeldaFila(tabla, nombre, fCelda, Element.ALIGN_LEFT, fondo);
                agregarCeldaFila(tabla, String.valueOf(cantidad), fCelda, Element.ALIGN_CENTER, fondo);
                agregarCeldaFila(tabla, formatearMoneda(precio), fCelda, Element.ALIGN_RIGHT, fondo);
                agregarCeldaFila(tabla, formatearMoneda(subtotal), fCelda, Element.ALIGN_RIGHT, fondo);
                filapar = !filapar;
            }
        }

        doc.add(tabla);
    }

    private void agregarCeldaCabecera(PdfPTable tabla, String texto, Font font, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBackgroundColor(COLOR_CABECERA_TABLA);
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(8);
        celda.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(celda);
    }

    private void agregarCeldaFila(PdfPTable tabla, String texto, Font font,
                                  int alineacion, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBackgroundColor(fondo);
        celda.setHorizontalAlignment(alineacion);
        celda.setPaddingTop(6);
        celda.setPaddingBottom(6);
        celda.setPaddingLeft(8);
        celda.setPaddingRight(8);
        celda.setBorderColor(new Color(226, 232, 240));
        celda.setBorderWidthTop(0);
        celda.setBorderWidthLeft(0);
        celda.setBorderWidthRight(0);
        celda.setBorderWidthBottom(1);
        tabla.addCell(celda);
    }

    // ── Resumen de totales ────────────────────────────────────────────────────

    private void agregarResumenTotales(Document doc, FacturaEntidad factura) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{1f, 1f});

        // Celda izquierda vacía
        PdfPCell vacia = new PdfPCell();
        vacia.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(vacia);

        // Celda derecha con el cuadro de totales
        PdfPTable tablaTotales = new PdfPTable(2);
        tablaTotales.setWidthPercentage(100);

        Font fLabel = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_TEXTO_GRIS);
        Font fValor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font fTotalLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);
        Font fTotalValor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);

        agregarFilaTotales(tablaTotales, "Total Neto:", formatearMoneda(factura.getTotal_Neto()),
            fLabel, fValor, Color.WHITE);
        agregarFilaTotales(tablaTotales, "IVA (19%):", formatearMoneda(factura.getIva()),
            fLabel, fValor, COLOR_FILA_PAR);
        agregarFilaTotales(tablaTotales, "TOTAL A PAGAR:", formatearMoneda(factura.getPrecio_Total()),
            fTotalLabel, fTotalValor, COLOR_PRIMARIO);

        PdfPCell celdaTotales = new PdfPCell(tablaTotales);
        celdaTotales.setBorder(Rectangle.NO_BORDER);
        celdaTotales.setPadding(0);
        wrapper.addCell(celdaTotales);

        doc.add(wrapper);
    }

    private void agregarFilaTotales(PdfPTable tabla, String label, String valor,
                                    Font fLabel, Font fValor, Color fondo) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, fLabel));
        cLabel.setBackgroundColor(fondo);
        cLabel.setPadding(8);
        cLabel.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(cLabel);

        PdfPCell cValor = new PdfPCell(new Phrase(valor, fValor));
        cValor.setBackgroundColor(fondo);
        cValor.setPadding(8);
        cValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cValor.setBorder(Rectangle.NO_BORDER);
        tabla.addCell(cValor);
    }

    // ── Pie de página ─────────────────────────────────────────────────────────

    private void agregarPiePagina(Document doc) throws DocumentException {
        LineSeparator linea = new LineSeparator(1, 100, new Color(203, 213, 225), Element.ALIGN_CENTER, 0);
        doc.add(new Chunk(linea));
        agregarEspaciado(doc, 6);

        Font fPie = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXTO_GRIS);
        Paragraph pie = new Paragraph("Este documento es una representación impresa de la factura electrónica.", fPie);
        pie.setAlignment(Element.ALIGN_CENTER);
        doc.add(pie);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private void agregarEspaciado(Document doc, float altura) throws DocumentException {
        doc.add(new Paragraph(altura, " "));
    }

    private String formatearMoneda(float valor) {
        return NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(valor);
    }
}
