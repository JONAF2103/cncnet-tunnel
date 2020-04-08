import {from, Observable} from "rxjs";
import {environment} from "../../environments/environment";

export abstract class NetworkService {

  apiHost = environment.apiHost;

  get(url: string, headers?: Headers): Observable<Response> {
    const requestInfo: RequestInit = {
      method: 'GET',
      headers: headers
    };
    return from(new Promise<Response>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        resolve(response);
      }).catch(error => reject(error));
    }));
  }

  post(url: string, body: any, headers?: Headers): Observable<Response> {
    const requestInfo: RequestInit = {
      method: 'POST',
      headers: headers,
      body: body ? JSON.stringify(body) : null
    };
    return from(new Promise<Response>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        resolve(response);
      }).catch(error => reject(error));
    }));
  }

  put(url: string, body: Response, headers?: Headers): Observable<Response> {
    const requestInfo: RequestInit = {
      method: 'PUT',
      headers: headers,
      body: body ? JSON.stringify(body) : null
    };
    return from(new Promise<Response>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        resolve(response);
      }).catch(error => reject(error));
    }));
  }

  getEntity<T>(url: string, headers?: Headers): Observable<T> {
    const requestInfo: RequestInit = {
      method: 'GET',
      headers: headers
    };
    return from(new Promise<T>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        response.json().then(jsonEntity => resolve(jsonEntity as T));
      }).catch(error => reject(error));
    }));
  }

  postEntity<T>(url: string, body: any, headers?: Headers): Observable<T> {
    const requestInfo: RequestInit = {
      method: 'POST',
      headers: headers,
      body: body ? JSON.stringify(body) : null
    };
    return from(new Promise<T>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        response.json().then(jsonEntity => resolve(jsonEntity as T));
      }).catch(error => reject(error));
    }));
  }

  putEntity<T>(url: string, body: Response, headers?: Headers): Observable<T> {
    const requestInfo: RequestInit = {
      method: 'PUT',
      headers: headers,
      body: body ? JSON.stringify(body) : null
    };
    return from(new Promise<T>((resolve, reject) => {
      const request = new Request(this.apiHost + url, requestInfo);
      fetch(request).then(response => {
        response.json().then(jsonEntity => resolve(jsonEntity as T));
      }).catch(error => reject(error));
    }));
  }
}
